package com.mindex.challenge.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("compensation")
public class Compensation {
    @Id
    private String compensationId;
    private String employeeId;

    @NotNull
    @Positive
    private BigDecimal salary;

    @NotNull
    private LocalDate effectiveDate;
}
