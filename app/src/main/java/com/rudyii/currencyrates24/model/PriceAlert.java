package com.rudyii.currencyrates24.model;

public class PriceAlert {
    public long id;
    public String ccy;        // "USD" or "EUR"
    public String type;       // "BUY" or "SALE"
    public String condition;  // "ABOVE" or "BELOW"
    public double targetRate;
    public boolean active;

    public PriceAlert(long id, String ccy, String type, String condition, double targetRate, boolean active) {
        this.id = id;
        this.ccy = ccy;
        this.type = type;
        this.condition = condition;
        this.targetRate = targetRate;
        this.active = active;
    }
}