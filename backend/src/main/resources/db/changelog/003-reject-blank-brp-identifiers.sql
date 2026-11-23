ALTER TABLE brp_catalog
    ADD CONSTRAINT brp_code_nonblank_ck
        CHECK (code IS NULL OR btrim(code, E' \t') <> ''),
    ADD CONSTRAINT brp_property_registration_nonblank_ck
        CHECK (property_registration IS NULL OR btrim(property_registration, E' \t') <> ''),
    ADD CONSTRAINT brp_license_plate_nonblank_ck
        CHECK (license_plate IS NULL OR btrim(license_plate, E' \t') <> '');
