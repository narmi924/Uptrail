package com.uptrail.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes optional web features as ordinary template model attributes. */
@ControllerAdvice(annotations = Controller.class)
public class WebFeaturesAdvice {

    private final boolean restEnabled;

    public WebFeaturesAdvice(@Value("${uptrail.web.rest-enabled:false}") boolean restEnabled) {
        this.restEnabled = restEnabled;
    }

    @ModelAttribute("restEnabled")
    public boolean restEnabled() {
        return restEnabled;
    }
}
