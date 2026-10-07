"""A small model for the tests of apply.py and apply_roles.py, because model.json has no objects in F1.

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
