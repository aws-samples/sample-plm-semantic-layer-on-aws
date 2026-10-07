# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Frontend tools of the AG-UI contract.

The model calls them like any tool; they do nothing on the server. The browser receives the
TOOL_CALL_* events and paints the screen from the arguments. The part tools answer an
acknowledgement that names the ids read through their normalised form and the ids no part of the
product on screen has (part_ids), the others None. The docstrings are what the model reads to
decide when to call each one.
"""

from strands import tool

from . import part_ids


@tool
def highlight_interfaces(ids: list[str], caption: str | None = None) -> None:
    """Highlight interfaces on the user's screen. FRONTEND tool: returns None; the browser
    lights the listed interfaces up.

    Call it every time your answer names one or more interfaces (after list_interfaces,
    interface_check, where_used or impact_of_change), with the exact interface ids a tool
    returned.

    Args:
        ids: interface ids exactly as a tool returned them, never invented.
        caption: one short sentence saying why these interfaces are highlighted.
    """
    return None


@tool
def open_evidence(interface: str, endpoint: str, tab: str | None = None) -> None:
    """Open the evidence panel of one interface on the user's screen. FRONTEND tool: returns
    None; the browser opens the panel.

    Call it when the user asks WHY an interface has its status or HOW a figure was obtained:
    the panel shows the arm of evidence behind your claim (the request sent to that endpoint,
    its SQL when the endpoint is Ontop, the triples and tables that came back).

    Args:
        interface: the interface id exactly as a tool returned it.
        endpoint: the endpoint (evidence arm) to open, as named in the `provenance` of a
            tool result or in the `evidence` tool's answer.
        tab: optional tab of the panel to focus, one of 'arm' (the SPARQL request sent to
            that endpoint), 'sql' (Ontop's generated SQL), 'rows' (native rows), 'triples',
            'r2rml', 'links', 'graph', 'shapes' or 'report'.
    """
    return None


@tool
def render_table(title: str, columns: list[str], rows: list[list]) -> None:
    """Render a table on the user's screen. FRONTEND tool: returns None; the browser paints it.

    Call it whenever the answer is a list of several items (interfaces, parts, features,
    violations) instead of writing the list as prose.

    Args:
        title: table title.
        columns: column headers in display order.
        rows: one list of cell values per row, aligned 1:1 with columns; every value taken
            from a tool result.
    """
    return None


# The viewer and what is loaded. Part ids are the native ids the tools return; they are unique
# across products.


@tool
def highlight_parts(ids: list[str], caption: str | None = None) -> str:
    """Outline parts in the 3D viewer in their site's colour, everything else unchanged.
    FRONTEND tool: the browser draws the outlines; the answer names any id no part has.

    Call it when the user asks to highlight parts, to see them in context or where they are, and
    every time another answer names one or more parts (after parts, where_used, bom,
    bom_where_used, export_status or any tool that returned part ids), with the exact part ids.

    Args:
        ids: native part ids exactly as a tool returned them, never invented.
        caption: one short sentence shown over the viewer saying why these parts are outlined.
    """
    return part_ids.acknowledge(ids, "Outlined")


@tool
def isolate_parts(ids: list[str], caption: str | None = None, context_ids: list[str] | None = None) -> str:
    """Show only these parts in the 3D viewer, at full opacity, with the context parts faded and
    everything else hidden, and fit the view to them. FRONTEND tool: the answer names any id no
    part has.

    Call it to show parts the user asks for ("show both wings": the parts find_parts found, one
    call for every group asked for, the caption naming each group), and for an answer that
    crosses sites (a bill-of-materials roll-up, where a part is used):
    `ids` are the parts the answer is about, `context_ids` the parts on the far side of their
    interfaces. After impact_of_change, `ids` are the impacted parts (the other side of the
    interfaces it returns) and `context_ids` the part that changes (the part named, or the part
    carrying the feature named). After parts_between_stations, `ids` are the parts inside the
    range and `context_ids` the parts crossing one of its ends.

    Args:
        ids: native part ids exactly as a tool returned them, drawn at full opacity.
        caption: one short sentence shown over the viewer saying what is isolated.
        context_ids: native part ids drawn faded around them.
    """
    return part_ids.acknowledge([*ids, *(context_ids or [])], "Isolated")


@tool
def zoom_to_part(id: str) -> str:
    """Fit the 3D view to one part, leaving what is drawn unchanged. FRONTEND tool: the answer
    says when no part has the id.

    Call it when the user asks where a part is or to look at one part.

    Args:
        id: the native part id exactly as a tool returned it.
    """
    return part_ids.acknowledge([id], "Zoomed")


@tool
def clear_view() -> None:
    """Put the 3D viewer back to the normal view of what is loaded: no outline, nothing isolated,
    the whole product or subtree in view. FRONTEND tool: returns None.

    Call it when the user asks to show everything again or the question moves away from the
    parts outlined or isolated.
    """
    return None


@tool
def open_subtree(root: str, product: str | None = None) -> None:
    """Load the subtree of one assembly on every screen: the assembly's parts, its breadcrumb, and
    the parts across its interfaces faded as context. FRONTEND tool: returns None.

    Call it when the request names one assembly ("show me the gearbox", "what is in the
    nacelle"): find the assembly's id with find_parts (a group with match assembly) or bom first.
    For a group of parts or several things, use isolate_parts.

    Args:
        root: the native id of the assembly, exactly as a tool returned it.
        product: the product key holding the assembly, as the products tool lists it, when it is
            not the product on screen.
    """
    part_ids.open_product(product)
    return None


@tool
def open_product(product: str) -> None:
    """Load a whole product on every screen, leaving any subtree. FRONTEND tool: returns None.

    Call it when the user asks to look at another product, or at the whole of the product on
    screen after a subtree.

    Args:
        product: the product key, as the products tool lists it.
    """
    part_ids.open_product(product)
    return None


@tool
def open_screen(screen: str) -> None:
    """Switch the user's screen. FRONTEND tool: returns None.

    Call it when the answer is best read on another screen: 'check' (the 3D viewer and the
    interface check), 'paths' (the paths and flows through the product), 'bom' (the bill of materials), 'catalogue' (the PLMs' data catalogue and
    mappings), 'flow' (the data flow of the last answer), 'architecture' or 'rules' (the rules
    and the shapes that state them).

    Args:
        screen: one of 'check', 'paths', 'bom', 'catalogue', 'flow', 'architecture', 'rules'.
    """
    return None
