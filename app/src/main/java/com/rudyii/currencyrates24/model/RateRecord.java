package com.rudyii.currencyrates24.model;

public class RateRecord {
    public long id;
    public String ccy;
    public String baseCcy;
    public double buy;
    public double sale;
    public long timestamp;

    public RateRecord(long id, String ccy, String baseCcy, double buy, double sale, long timestamp) {
        this.id = id;
        this.ccy = ccy;
        this.baseCcy = baseCcy;
        this.buy = buy;
        this.sale = sale;
        this.timestamp = timestamp;
    }
}