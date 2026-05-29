package io.libra.api.controller;

import io.libra.api.dto.response.InstrumentResponse;
import io.libra.api.dto.request.RegisterCurrencyPairRequest;
import io.libra.reference.port.ReferenceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
@RequiredArgsConstructor
public class ReferenceController {

    private final ReferenceDataService referenceData;

    @GetMapping
    public List<InstrumentResponse> listActive() {
        return referenceData.listActiveInstruments().stream().map(InstrumentResponse::from).toList();
    }

    @PostMapping("/pairs")
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentResponse registerPair(@RequestBody RegisterCurrencyPairRequest request) {
        return InstrumentResponse.from(referenceData.registerCurrencyPair(request.toCommand()));
    }
}
