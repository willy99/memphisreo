-- Пруф мультикраїнності з дня 1 — docs/business-plan.md §2, docs/domain-model.md §4.
INSERT INTO control_plane.country (code, name, default_currency, default_locale, region, required_property_fields)
VALUES (
    'UA', 'Україна', 'UAH', 'uk-UA', 'UA',
    $json$
{
        "type": "object",
        "properties": {
            "cadastral_number": {
                "type": "string",
                "pattern": "^[0-9]{10}:[0-9]{2}:[0-9]{3}:[0-9]{4}$",
                "x-label-uk": "Кадастровий номер"
            }
        },
        "required": ["cadastral_number"]
    }$json$::jsonb
);

INSERT INTO control_plane.country (code, name, default_currency, default_locale, region, required_property_fields)
VALUES (
    'DE', 'Deutschland', 'EUR', 'de-DE', 'EU',
    $json$
{
        "type": "object",
        "properties": {
            "energy_certificate": {
                "type": "string",
                "enum": ["A", "B", "C", "D", "E", "F", "G"],
                "x-label-uk": "Клас енергоефективності"
            }
        },
        "required": ["energy_certificate"]
    }$json$::jsonb
);
