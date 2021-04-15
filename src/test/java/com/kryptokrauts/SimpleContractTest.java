package com.kryptokrauts;

import com.kryptokrauts.contraect.generated.SimpleContract;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class SimpleContractTest extends BaseTest {

  @Test
  public void createAndVerifyHamster() {
    SimpleContract sc = new SimpleContract(config, null);
    String contractId = sc.deploy().getValue1();
    new SimpleContract(config, contractId, true);
  }
}
