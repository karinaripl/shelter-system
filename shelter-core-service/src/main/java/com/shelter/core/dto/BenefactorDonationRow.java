package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Пожертвования благотворителя за период; benefactor = null (anonymous = true) — анонимные. */
public record BenefactorDonationRow(
        BenefactorBrief benefactor,
        boolean anonymous,
        int moneyCount,
        BigDecimal moneyTotal,
        int goodsCount,
        LocalDate lastDonation
) {
}
