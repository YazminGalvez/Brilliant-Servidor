CREATE TABLE IF NOT EXISTS usuarios (
    id TEXT PRIMARY KEY,
    nombre TEXT NOT NULL,
    nombre_normalizado TEXT NOT NULL UNIQUE,
    hash_contrasena TEXT NOT NULL,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL
);
