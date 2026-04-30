package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.ConsentTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootTest
@TestPropertySource(properties = {
        "his.document-export.enabled=true",
        "his.document-export.path=Z:/autoasociar"
})
public class HisDocumentExportServiceProdTest {

    private static final Logger log = LoggerFactory.getLogger(HisDocumentExportServiceProdTest.class);

    @Autowired
    private HisDocumentExportService service;

    @Test
    public void testExportWith99999() {
        ConsentRequest req = new ConsentRequest();
        req.setId(99999L);
        req.setNhc("99999");

        ConsentTemplate template = new ConsentTemplate();
        template.setName("consentimientoInformado");
        req.setTemplate(template);

        File pdfDir = new File("pdfs");
        File[] pdfs = pdfDir.listFiles((dir, name) -> name.endsWith(".pdf"));

        if (pdfs != null && pdfs.length > 0) {
            String pdfPath = pdfs[0].getAbsolutePath();
            log.info("Using PDF: {}", pdfPath);
            service.exportSignedConsent(req, pdfPath);
            log.info("Export completed for NHC 99999. File should be in Z:/autoasociar");
        } else {
            log.info("No PDFs found in 'pdfs' folder to use for testing.");
        }
    }
}
