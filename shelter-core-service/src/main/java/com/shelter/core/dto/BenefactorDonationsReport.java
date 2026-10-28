package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Пожертвования по благотворителям за период: больше денег — выше. */
public record BenefactorDonationsReport(
        LocalDate from,
        LocalDate to,
        List<BenefactorDonationRow> benefactors,
        BigDecimal moneyTotal,
        int goodsCount
) {
}
