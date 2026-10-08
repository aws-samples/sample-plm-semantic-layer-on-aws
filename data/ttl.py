# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""A Turtle reader for the subset ontology/atelier.ttl is written in, for data/purchasing.py (standard library only).

It reads @prefix directives, IRIs, prefixed names, `a`, string literals with a language tag or a datatype, bare
numbers and booleans, predicate lists (;), object lists (,), blank nodes ([ ]) and collections (( )). A term is a
plain string: an IRI expanded in full, a blank node as `_:bN`, a literal as Literal(text, lang, datatype).
"""

import re
from typing import NamedTuple

RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
XSD = "http://www.w3.org/2001/XMLSchema#"

TOKEN = re.compile(r"""
    (?P<space>\s+|\#[^\n]*)
  | (?P<iri><[^>]*>)
  | (?P<string>"(?:[^"\\]|\\.)*")(?:@(?P<lang>[A-Za-z]+(?:-[A-Za-z0-9]+)*)|\^\^(?P<dtype><[^>]*>|[A-Za-z][\w.-]*:[\w.-]*))?
  | (?P<directive>@prefix)
  | (?P<number>[+-]?(?:\d+\.\d+|\d+))(?![\w:])
  | (?P<name>(?:[A-Za-z][\w.-]*)?:[\w.-]*|a(?=\s)|true|false)
  | (?P<punct>[.;,\[\]()])
""", re.X)


class Literal(NamedTuple):
    text: str
    lang: str | None = None
    datatype: str | None = None


def _tokens(text):
    pos = 0
    while pos < len(text):
        m = TOKEN.match(text, pos)
        if m is None:
            raise ValueError(f"ttl: cannot read at {text[pos:pos + 40]!r}")
        pos = m.end()
        if m.lastgroup != "space":
            yield m


def _unescape(quoted):
    return re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t"}.get(m.group(1), m.group(1)), quoted[1:-1])


def parse(text):
    """The triples of a Turtle document, as (subject, predicate, object) tuples."""
    tokens, prefixes, triples, blank = list(_tokens(text)), {}, [], [0]
    i = 0

    def fresh():
        blank[0] += 1
        return f"_:b{blank[0]}"

    def peek():
        return tokens[i].group(0) if i < len(tokens) else None

    def expand(name):
        prefix, local = name.split(":", 1)
        return prefixes[prefix] + local

    def term():
        nonlocal i
        m = tokens[i]
        i += 1
        if m.group("iri"):
            return m.group("iri")[1:-1]
        if m.group("string"):
            dtype = m.group("dtype")
            if dtype:
                dtype = dtype[1:-1] if dtype.startswith("<") else expand(dtype)
            return Literal(_unescape(m.group("string")), m.group("lang"), dtype)
        if m.group("number"):
            return Literal(m.group("number"), None, XSD + ("decimal" if "." in m.group("number") else "integer"))
        if m.group("name"):
            name = m.group("name")
            if name == "a":
                return RDF + "type"
            if name in ("true", "false"):
                return Literal(name, None, XSD + "boolean")
            return expand(name)
        if m.group(0) == "[":
            node = fresh()
            if peek() != "]":
                predicates(node)
            expect("]")
            return node
        if m.group(0) == "(":
            items = []
            while peek() != ")":
                items.append(term())
            expect(")")
            head = RDF + "nil"
            for item in reversed(items):
                node = fresh()
                triples.extend([(node, RDF + "first", item), (node, RDF + "rest", head)])
                head = node
            return head
        raise ValueError(f"ttl: unexpected {m.group(0)!r}")

    def expect(punct):
        nonlocal i
        if peek() != punct:
            raise ValueError(f"ttl: expected {punct!r}, found {peek()!r}")
        i += 1

    def predicates(subject):
        nonlocal i
        while True:
            predicate = term()
            while True:
                triples.append((subject, predicate, term()))
                if peek() != ",":
                    break
                i += 1
            if peek() != ";":
                return
            while peek() == ";":
                i += 1
            if peek() in (".", "]"):
                return

    while i < len(tokens):
        if tokens[i].group("directive"):
            prefix, iri = tokens[i + 1].group(0), tokens[i + 2].group("iri")
            prefixes[prefix[:-1]] = iri[1:-1]
            i += 3
            expect(".")
            continue
        subject = term()
        if peek() != ".":
            predicates(subject)
        expect(".")
    return triples
