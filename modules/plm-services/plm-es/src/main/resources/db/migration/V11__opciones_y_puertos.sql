-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Opciones: el código de la opción bajo la que sola existe una pieza o una característica, vacío para un artículo de
-- la configuración base. Puerto: el nombre del punto de conexión que una pieza anfitriona ofrece a un emplazamiento de
-- instalación, el mismo de una opción a otra.
ALTER TABLE pieza        ADD COLUMN opcion VARCHAR(64);
ALTER TABLE conector     ADD COLUMN puerto VARCHAR(64);
ALTER TABLE conector     ADD COLUMN opcion VARCHAR(64);
ALTER TABLE remache      ADD COLUMN puerto VARCHAR(64);
ALTER TABLE remache      ADD COLUMN opcion VARCHAR(64);
ALTER TABLE acoplamiento ADD COLUMN puerto VARCHAR(64);
ALTER TABLE acoplamiento ADD COLUMN opcion VARCHAR(64);
