package com.kryptokrauts;

import com.kryptokrauts.contraect.generated.AdventOfCodeDay11;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
public class AdventOfCodeDay11Test extends BaseTest {
  private static String contractId;

  private static final String ERROR_MSG_SOLVE_1 =
      "An error occured calling function solve_1: [\"Out of gas\"]";
  private static final String SOLUTION_SOLVE_2 =
      "[ #  # #### ###  ###  ###  ####  ##    ##   ,  #  # #    #  # #  # #  # #    #  #    #   ,  #  # ###  #  # #  # #  # ###  #       #   ,  #  # #    ###  ###  ###  #    # ##    #   ,  #  # #    # #  #    # #  #    #  # #  #   ,   ##  #### #  # #    #  # #     ###  ##    ]";

  @BeforeAll
  public static void deploy() {
    AdventOfCodeDay11 adventOfCodeDay11 = new AdventOfCodeDay11(config, null);
    // deploy contract
    Pair<String, String> deployment = adventOfCodeDay11.deploy();
    String txHash = deployment.getValue0();
    contractId = deployment.getValue1();
    log.info("tx-hash of deployment: {}", txHash);
    log.info("contract id: {}", contractId);
    Assertions.assertNotNull(contractId);
  }

  @Test
  public void testSolve1() {
    AdventOfCodeDay11 adventOfCodeDay11 = new AdventOfCodeDay11(config, contractId);
    try {
      adventOfCodeDay11.solve_1();
      Assertions.fail("should have failed with 'Out of gas' error");
    } catch (Exception e) {
      // we expect an "Out of gas" error due to high gas consumption (4264437725)
      // see https://forum.aeternity.com/t/advent-of-code-2019-day-11-12/5541/4?u=marc0olo
      Assertions.assertEquals(ERROR_MSG_SOLVE_1, e.getMessage());
    }
  }

  @Test
  public void testSolve2() {
    AdventOfCodeDay11 adventOfCodeDay11 = new AdventOfCodeDay11(config, contractId);
    List<String> result = adventOfCodeDay11.solve_2();
    Assertions.assertEquals(SOLUTION_SOLVE_2, result.toString());
  }
}
