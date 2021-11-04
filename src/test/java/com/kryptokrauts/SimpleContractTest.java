package com.kryptokrauts;

import com.kryptokrauts.contraect.generated.SimpleContract;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
public class SimpleContractTest extends BaseTest {

  @Test
  public void deployment() {
    SimpleContract sc = new SimpleContract(config, null);
    String contractId = sc.deploy().getValue1();
    new SimpleContract(config, contractId, true);
    Assertions.assertTrue(sc.testTimestamp().longValue() > 0l);
  }
}
