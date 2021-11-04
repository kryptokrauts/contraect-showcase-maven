package com.kryptokrauts;

import com.kryptokrauts.contraect.generated.ContentValidation;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CVTest extends BaseTest {
  private static final Logger _logger = LoggerFactory.getLogger(CVTest.class);

  @Test
  public void testDeploy() {
    ContentValidation cv = new ContentValidation(config, null);
    String txHash = cv.deploy().getValue0();
    _logger.info(
        "Info: {}",
        aeternityService.info.blockingGetTransactionInfoByHash(txHash).getCallInfo().getCallerId());
    _logger.info("ByHash: {}", aeternityService.info.blockingGetTransactionByHash(txHash));
  }
}
