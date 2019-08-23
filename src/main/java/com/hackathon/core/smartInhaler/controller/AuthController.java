package com.hackathon.core.smartInhaler.controller;

import com.hackathon.core.smartInhaler.exception.ApplicationException;
import com.hackathon.core.smartInhaler.model.LoginRequest;
import com.hackathon.core.smartInhaler.service.AuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.text.ParseException;

@RestController
@RequestMapping(value = "${app.context.path}")
@Api(value = "SmartInhaler", description = "The project is about the development of a smart inhaler for asthmatic patients. The project aims to solve the major problems revolving around the patients’ adherence to the dosages that have been prescribed by their doctors.")
@Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    @GetMapping(value = "basic-token", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ApiOperation(value = "A service to get basic Token for authnetication", response = ResponseEntity.class, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity getBasicToken() {
      log.info("Inside getBasicToken");
        return authService.getBasicToken();
    }

    @PostMapping(value = "login", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ApiOperation(value = "A service to login to Smart Inhaler account", response = ResponseEntity.class, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity  login(@RequestHeader("authorization") String authorization, @RequestBody LoginRequest loginRequest) throws IOException, ApplicationException {
        log.info("Inside login");
        return authService.login(authorization, loginRequest);
    }

    @GetMapping(value = "user-dashboard", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ApiOperation(value = "A service is to login to get patient's details for Dashboard", response = ResponseEntity.class, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity  getUserDashboardDetails(@RequestHeader("authorization") String authorization, @RequestParam String userGuid) throws IOException, ParseException {
        log.info("Inside getUserDashboardDetails");
        return authService.getUserDashboardDetails(authorization, userGuid);
    }

    @GetMapping(value = "attribute-history/{fromDate}{/todate}/{uniqueId}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ApiOperation(value = "A service to get Dosage History of patient", response = ResponseEntity.class, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity  getAttributeDetails(@RequestHeader("authorization") String authorization,
                                               @RequestParam String fromDate,
                                               @RequestParam String toDate,
                                               @RequestParam String uniqueId) {
        log.info("Inside getAttributeDetails");
        return authService.getAttributeDetails(authorization, fromDate, toDate, uniqueId);
    }
}
