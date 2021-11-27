package com.kryptokrauts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kryptokrauts.contraect.generated.SophiaTypes;
import com.kryptokrauts.contraect.generated.SophiaTypes.ChainTTL;
import com.kryptokrauts.contraect.generated.SophiaTypes.ChainTTL.ChainTTLType;
import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
public class SophiaTypesTest extends BaseTest {

  private static SophiaTypes contract;

  @BeforeAll
  private static void deployContract() {
    contract = new SophiaTypes(config, null);
    Pair<String, String> contractId = contract.deploy();
    log.info("Deployed contract: {}", contractId);
  }

  @Test
  public void testTtl() {
    ChainTTL ttl = new ChainTTL(BigInteger.valueOf(42l), ChainTTLType.RelativeTTL);
    assertEquals(ttl, contract.testTtl(ttl));
  }
}
