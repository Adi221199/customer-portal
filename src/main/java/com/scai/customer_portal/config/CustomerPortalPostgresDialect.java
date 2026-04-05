package com.scai.customer_portal.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * PostgreSQL has no {@code year(date)} / {@code month(date)} (MySQL-style). Criteria
 * {@code function("year", ...)} must map to {@code extract(...)}.
 */
public class CustomerPortalPostgresDialect extends PostgreSQLDialect {

	@Override
	public void initializeFunctionRegistry(FunctionContributions functionContributions) {
		super.initializeFunctionRegistry(functionContributions);
		SqmFunctionRegistry registry = functionContributions.getFunctionRegistry();
		TypeConfiguration typeConfiguration = functionContributions.getTypeConfiguration();
		BasicTypeRegistry basicTypes = typeConfiguration.getBasicTypeRegistry();
		var intType = basicTypes.resolve(StandardBasicTypes.INTEGER);
		registry.registerPattern("year", "extract(year from ?1)", intType);
		registry.registerPattern("month", "extract(month from ?1)", intType);
	}
}
