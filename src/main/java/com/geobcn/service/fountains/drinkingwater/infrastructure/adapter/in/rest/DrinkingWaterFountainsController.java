package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.geobcn.service.infrastructure.generated.openapi.api.DrinkingWaterFountainsApiDelegate;
import com.geobcn.service.infrastructure.generated.openapi.model.DrinkingWaterFountainResponse;
import com.geobcn.service.infrastructure.generated.openapi.model.DrinkingWaterFountainsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DrinkingWaterFountainsController implements DrinkingWaterFountainsApiDelegate {

    @Override
    public ResponseEntity<DrinkingWaterFountainsResponse> getDrinkingWaterFountains
            (String acceptLanguage, String xRequestID, String pageSize, String paginationKey) {
        return new ResponseEntity<>(new DrinkingWaterFountainsResponse(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<DrinkingWaterFountainResponse> getDrinkingWaterFountainsDrinkingWaterFountainId(
            String acceptLanguage, String xRequestID, String drinkingWaterFountainId) {
        return new ResponseEntity<>(new DrinkingWaterFountainResponse(), HttpStatus.OK);
    }

}
