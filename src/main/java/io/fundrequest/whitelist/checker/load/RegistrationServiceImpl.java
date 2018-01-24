package io.fundrequest.whitelist.checker.load;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import io.fundrequest.whitelist.checker.kyc.domain.KYCEntry;
import io.fundrequest.whitelist.checker.kyc.service.KYCService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class RegistrationServiceImpl implements RegistrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationServiceImpl.class);
    private String spreadsheetId;
    private String googleClientSecret;
    private KYCService kycService;

    public RegistrationServiceImpl(@Value("${io.fundrequest.tokensale.status.spreadsheet-id}") String spreadsheetId, @Value("${io.fundrequest.tokensale.status.google-sheets-client-secret}") String googleClientSecret, KYCService kycService) {
        this.spreadsheetId = spreadsheetId;
        this.googleClientSecret = googleClientSecret;
        this.kycService = kycService;
    }

    @Override
    @Scheduled(fixedDelay = 300000L)
    public void load() {
        try {
            importFromSheets();
            LOGGER.info("Imported new data");
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException("unable to load");
        }
    }

    private void importFromSheets() throws Exception {
        String range = "A3:L";
        ValueRange response = getSheetsService().spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        List<List<Object>> values = response.getValues();
        Set<KYCEntry> entries = values.stream().map(this::createKycEntry).collect(Collectors.toSet());
        kycService.insert(entries);
    }

    private KYCEntry createKycEntry(List<Object> row) {
        String address = getRowValue(row, 5);
        String referredBy = getReferredBy(row, 10);
        return new KYCEntry()
                .setAddress(address)
                .setReferredBy(referredBy)
                .setReferralKey(address)
                .setStatus(getRowValue(row, 11));
    }

    private String getReferredBy(List<Object> row, int i) {
        String key = getRowValue(row, i);
        if (StringUtils.isNotBlank(key)) {
            key = key.replace("dke02sx6", "");
            key = key.replace("dke02sx", "");
            if (key.length() > 42) {
                key = key.substring(0, 42);
            }
            return key;
        }
        return null;
    }

    private String getRowValue(List row, int i) {
        return i < row.size() ? row.get(i).toString() : null;
    }

    private Sheets getSheetsService() throws Exception {
        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(), authorize())
                .setApplicationName("TOKENSALE-STATUS")
                .build();
    }

    private Credential authorize() throws IOException {
        List<String> scopes = Collections.singletonList(SheetsScopes.SPREADSHEETS);
        return GoogleCredential
                .fromStream(new ByteArrayInputStream(this.googleClientSecret.getBytes()))
                .createScoped(scopes);
    }

}