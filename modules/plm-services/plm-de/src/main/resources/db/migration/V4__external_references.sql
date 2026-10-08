-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Externe Verweise: ein Bauteil verwendet ein Teil eines anderen Werks. Das Werk kopiert das fremde Teil nie,
-- es nennt es mit seiner URN (urn:plm:<werk>:part:<Teilenummer>) und der Revision, die es erwartet. Ob das Ziel
-- existiert und ob seine Revision noch gilt, weiß nur die semantische Schicht, die beide Seiten sieht.
CREATE TABLE externer_verweis (
    id                  VARCHAR(16)    PRIMARY KEY,
    teil_nr             VARCHAR(64)    NOT NULL REFERENCES bauteil (teil_nr),
    urn                 VARCHAR(160)   NOT NULL,
    menge               NUMERIC(12, 3) NOT NULL,
    erwartete_revision  VARCHAR(8),
    bemerkung           VARCHAR(400)
);

CREATE INDEX externer_verweis_teil_nr_idx ON externer_verweis (teil_nr);
