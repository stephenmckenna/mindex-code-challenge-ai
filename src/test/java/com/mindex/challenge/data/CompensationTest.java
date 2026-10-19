package com.mindex.challenge.data;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class CompensationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @Autowired
    private JacksonTester<Compensation> json;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void serializesEffectiveDateAsIsoDateAndKeepsSalaryScale() throws Exception {
        JsonContent<Compensation> written = json.write(compensation(new BigDecimal("85000.00"), LocalDate.of(2025, 1, 1)));

        assertThat(written).extractingJsonPathStringValue("$.effectiveDate").isEqualTo("2025-01-01");
        assertThat(written.getJson()).contains("\"salary\":85000.00");
    }

    @Test
    void deserializesIsoDateAndExactSalary() throws Exception {
        Compensation compensation = json.parseObject("""
                {"salary": 85000.10, "effectiveDate": "2025-01-01"}
                """);

        assertThat(compensation.getSalary()).isEqualTo(new BigDecimal("85000.10"));
        assertThat(compensation.getEffectiveDate()).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void validCompensationHasNoViolations() {
        assertThat(validator.validate(compensation(new BigDecimal("0.01"), LocalDate.of(2025, 1, 1)))).isEmpty();
    }

    static Stream<Arguments> invalidCompensations() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        return Stream.of(
                Arguments.of("missing salary", compensation(null, date), "salary"),
                Arguments.of("zero salary", compensation(BigDecimal.ZERO, date), "salary"),
                Arguments.of("negative salary", compensation(new BigDecimal("-1"), date), "salary"),
                Arguments.of("missing effectiveDate", compensation(BigDecimal.TEN, null), "effectiveDate"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCompensations")
    void invalidCompensationIsRejectedOnTheRightField(String description, Compensation compensation, String field) {
        Set<ConstraintViolation<Compensation>> violations = validator.validate(compensation);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly(field);
    }

    private static Compensation compensation(BigDecimal salary, LocalDate effectiveDate) {
        return Compensation.builder()
                .salary(salary)
                .effectiveDate(effectiveDate)
                .build();
    }
}
