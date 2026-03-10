package com.chpc.backend.dto;

import lombok.Data;

@Data
public class PenEventDto {
    private Double x;
    private Double y;
    private Double pressure;
    private String status;
    private Double maxX;
    private Double maxY;
    private Double maxPressure;
}
