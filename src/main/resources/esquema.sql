CREATE TABLE IF NOT EXISTS usuarios (
    id TEXT PRIMARY KEY,
    nombre TEXT NOT NULL,
    nombre_normalizado TEXT NOT NULL UNIQUE,
    hash_contrasena TEXT NOT NULL,
    creado_en TEXT NOT NULL,
    actualizado_en TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sesiones (
    id TEXT PRIMARY KEY,
    usuario_id TEXT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    creado_en TEXT NOT NULL,
    expira_en TEXT NOT NULL,
    revocado_en TEXT
);

CREATE INDEX IF NOT EXISTS sesiones_usuario_id ON sesiones (usuario_id);
