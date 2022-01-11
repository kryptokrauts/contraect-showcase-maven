package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.constants.Network;
import com.kryptokrauts.aeternity.sdk.service.aeternity.AeternityServiceConfiguration;
import com.kryptokrauts.contraect.generated.TokenMigration;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TokenMigrationTest extends BaseTest {

  private static TokenMigration tokenMigration;

  private static String ethAddress =
      "0X932CF9910672B8A26BD31141FF8F11E9B7DFA6E2"; // this address is already migrated
  private static BigInteger tokenAmount = new BigInteger("349185165700000000000");
  private static BigInteger leafIndex = new BigInteger("12183");
  private static List<String> siblings =
      Arrays.asList(
          "051BDA22F68DA9DF313AF7CECC674719144C0F8ECA3FAB3198D7142B9CEAAC26",
          "0CAB2FB45014E7525AEE712958160543F6FD72F5240190FC3DD8D81DE8C50273",
          "06C6A716161F47FAB561CBB8396780248730967D7A29BB168C07FEF3A4D350E3",
          "8E50602C4E28386DEACF5E13B6E6A61A6DFAE36B9E173DB7A049303EC2E53DB3",
          "1B4D4F067BAFCD1FCCB4460AC31A05EED48563E7045DC4EF996E7D8C3EEB9EFD",
          "3BB0582BD65EA4C994CB9391533B782A7136FB711B47C512556A97313C973F26",
          "F884BF2270FACB34FD0A7C49092B102DFB2E43C42D3D38B44E394D9A258AEE93",
          "19DA2DF18294D5E3E264AEBB48DA4D3622457BDD1162C7416198C732978DC210",
          "D2888B644AE539E259B2D104214239F30B810ADE99874B5EFE68A0FD77CBC1A5",
          "AD206ED34B49DA709CF01A84D53489D7908D78952181E9E61C536799189FE411",
          "2FF75575202A4A1D4DF8F0888967A9E9ABEB3C364FFF1C9E9D51A946C7AD30F3",
          "58517514D61EA90D01AA0A8547BA02D98D393DE25A615FAB868828DF88CF6769",
          "AEEAFA5E07BB461264A856B13C3C346DFCD870EE48C1D4CE40A69DA720A8E2BD",
          "764CEF512A9223E21BD5650B55245188876A83B15BB5B3B418B87E28F1B74FB9",
          "7195B9E30E43FA2F188D69C5F61BB75A00CF639E7925CAF4C1FD2AF32D976B0B");

  @BeforeAll
  public static void init() {
    String contractId = "ct_eJhrbPPS4V97VLKEVbSCJFpdA4uyXiZujQyLqMFoYV88TzDe6";
    AeternityServiceConfiguration config =
        AeternityServiceConfiguration.configure()
            .baseUrl("https://mainnet.aeternity.io")
            // KeyPair required for read-only calls as long as
            // https://github.com/kryptokrauts/contraect-maven-plugin/issues/64 is not addressed
            .keyPair(keyPairService.generateKeyPair())
            .network(Network.MAINNET)
            .compile();
    tokenMigration = new TokenMigration(config, contractId);
  }

  @Test
  @Order(1)
  public void rootHash() {
    String rootHash = tokenMigration.root_hash();
    Assertions.assertEquals(
        "E4DBC69BF2783B81B0423DA3F5B684C1D37CCFAE798474525C4001DB42C67669", rootHash);
  }

  @Test
  @Order(2)
  public void containedInMerkleTree() {
    Boolean isInMerkleTree =
        tokenMigration.contained_in_merkle_tree(ethAddress, tokenAmount, leafIndex, siblings);
    Assertions.assertTrue(isInMerkleTree);
  }

  @Test
  @Order(3)
  public void migrated() {
    Boolean isMigrated = tokenMigration.is_migrated(ethAddress);
    Assertions.assertTrue(isMigrated);
  }
}
