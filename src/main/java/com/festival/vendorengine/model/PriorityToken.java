package com.festival.vendorengine.model;

public enum PriorityToken {
    VIP(100), STANDARD(0);

    private final int weight;

    PriorityToken(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
