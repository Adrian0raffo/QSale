package com.example.qsale.option.dto;

import com.example.qsale.location.dto.LocationDto;
import com.example.qsale.option.domain.OptionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class OptionRequestDto {

    @NotNull
    private OptionType type;

    @NotBlank
    @Size(min = 3, max = 120)
    private String label;

    @Size(max = 500)
    private String description;

    @Future
    private LocalDateTime startsAt;

    @Future
    private LocalDateTime endsAt;

    @FutureOrPresent
    private LocalDate startsOn;

    @FutureOrPresent
    private LocalDate endsOn;

    @Valid
    private LocationDto location;
}
