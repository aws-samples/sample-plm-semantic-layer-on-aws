-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Einbaulagen: eine Zeile je Verwendung eines Teils unter seinem übergeordneten Teil, laufend nummeriert. Die Lage
-- ist relativ zum Teil, wie seine CAD-Datei es zeichnet: Verschiebung in Millimetern, dann Drehung in Grad um die
-- festen Achsen x, y und z des Produktrahmens, in dieser Reihenfolge. Die Nummer 1 ist die Lage der CAD-Datei. Ein
-- Teil ohne Einbaulagen wird einmal verwendet, an der Lage seiner CAD-Datei. Die Zeile folgt der Stücklistenzeile auf
-- dem Bauteil: ein Wechsel des übergeordneten Teils nimmt die Einbaulagen mit, ein gelöschtes Teil löscht sie.
ALTER TABLE bauteil ADD CONSTRAINT bauteil_teil_nr_parent_id_key UNIQUE (teil_nr, parent_id);

CREATE TABLE einbaulage (
    parent_id  VARCHAR(64)    NOT NULL,
    teil_nr    VARCHAR(64)    NOT NULL,
    lfd_nr     INTEGER        NOT NULL CHECK (lfd_nr >= 1),
    x_mm       NUMERIC(12, 4) NOT NULL,
    y_mm       NUMERIC(12, 4) NOT NULL,
    z_mm       NUMERIC(12, 4) NOT NULL,
    rx_grad    NUMERIC(9, 4)  NOT NULL,
    ry_grad    NUMERIC(9, 4)  NOT NULL,
    rz_grad    NUMERIC(9, 4)  NOT NULL,
    PRIMARY KEY (parent_id, teil_nr, lfd_nr),
    FOREIGN KEY (teil_nr, parent_id) REFERENCES bauteil (teil_nr, parent_id) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX einbaulage_teil_nr_idx ON einbaulage (teil_nr);
