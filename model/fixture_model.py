"""A small model for the tests of apply.py and apply_roles.py: model.json (F2, 48 objects) is too big to exercise them.

Two objects, one relationship, one declared action and one derived field: enough to exercise the payloads, `u` and
"derived": true. Test helper only.
"""

FIXTURE_MODEL = {
    "enums": {"zone_type": ["DIRECTA", "INDIRECTA"]},
    "objects": [
        {
            "name": "zone",
            "label": "Zona",
            "pluralLabel": "Zonas",
            "fields": [
                {"name": "code", "label": "Código", "type": "TEXT", "required": True, "unique": True},
                {"name": "zone_type", "label": "Tipo", "type": "ENUM", "enum": "zone_type"},
                {"name": "area_ha", "label": "Área (ha)", "type": "DECIMAL", "derived": True},
            ],
        },
        {
            "name": "visit",
            "label": "Visita",
            "pluralLabel": "Visitas",
            "requiresReason": True,
            "actions": [{"name": "EDIT_ANY", "label": "Editar lo ajeno"}],
            "fields": [{"name": "visit_date", "label": "Fecha", "type": "DATE", "required": True}],
        },
    ],
    "relationships": [
        {"name": "visit_zone", "label": "Zona", "inverseLabel": "Visitas", "source": "visit", "target": "zone",
         "fieldName": "zone"},
    ],
}


# what F2 adds to apply.py (database-model.md §24): a self-reference, a MANY_TO_MANY, `indexed`, `defaultValue` and an
# `indexes` set that names a relationship's field (phase 3). zone comes after visit on purpose: the order of objects
# no longer matters, every object exists before the first relationship
F2_FIXTURE_MODEL = {
    "enums": {"zone_type": ["DIRECTA", "INDIRECTA"]},
    "objects": [
        {
            "name": "visit",
            "label": "Visita",
            "pluralLabel": "Visitas",
            "indexes": [["zone", "visit_date"]],
            "fields": [{"name": "visit_date", "label": "Fecha", "type": "DATE", "required": True, "indexed": True}],
        },
        {
            "name": "zone",
            "label": "Zona",
            "pluralLabel": "Zonas",
            "fields": [
                {"name": "code", "label": "Código", "type": "TEXT", "required": True, "unique": True},
                {"name": "zone_type", "label": "Tipo", "type": "ENUM", "enum": "zone_type", "defaultValue": "DIRECTA"},
                {"name": "active", "label": "Vigente", "type": "BOOLEAN", "required": True, "defaultValue": "true"},
            ],
        },
    ],
    "relationships": [
        {"name": "visit_zone", "label": "Zona", "inverseLabel": "Visitas", "source": "visit", "target": "zone",
         "fieldName": "zone", "required": True},
        {"name": "zone_parent_zone", "label": "Zona padre", "inverseLabel": "Zonas", "source": "zone", "target": "zone",
         "fieldName": "parent_zone"},
        {"name": "visit_zones", "type": "MANY_TO_MANY", "label": "Zonas recorridas", "inverseLabel": "Visitas",
         "source": "visit", "target": "zone"},
    ],
}
