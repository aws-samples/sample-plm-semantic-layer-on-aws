-- Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
-- SPDX-License-Identifier: MIT-0

-- Engranajes: número de dientes y módulo en milímetros en la fila de la pieza, vacíos para una pieza sin dentado.
-- Un piñón escalonado con varios dentados solo lleva el módulo común.
ALTER TABLE pieza ADD COLUMN numero_dientes  INTEGER;
ALTER TABLE pieza ADD COLUMN modulo_mm       NUMERIC(8, 3);
