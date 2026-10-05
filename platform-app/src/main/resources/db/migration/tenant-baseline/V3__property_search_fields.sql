-- Core-фільтровані поля виносимо з attributes (JSONB) у типізовані
-- колонки — саме за ними шукатимуть публічно. docs/domain-model.md §6.1.
ALTER TABLE property
    ADD COLUMN land_area_sqm numeric(10, 2),
    ADD COLUMN bedrooms integer,
    ADD COLUMN bathrooms integer,
    ADD COLUMN has_elevator boolean,
    ADD COLUMN parking_spaces integer,
    ADD COLUMN description text;
