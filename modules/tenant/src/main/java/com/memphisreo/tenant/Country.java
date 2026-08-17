package com.memphisreo.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * required_property_fields — JSON Schema (docs/domain-model.md §4), не
 * власний DSL; редагується через адмін-форму, ефект — без редеплою.
 */
@Entity
@Table(name = "country", schema = "control_plane")
@Getter
@Setter
@NoArgsConstructor
public class Country {

    @Id
    @Column(length = 2)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    @Column(name = "default_locale", nullable = false)
    private String defaultLocale;

    @Column(nullable = false)
    private String region;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_property_fields", columnDefinition = "jsonb", nullable = false)
    private String requiredPropertyFieldsJson;
}
