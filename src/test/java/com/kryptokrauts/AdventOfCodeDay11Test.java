package com.kryptokrauts;

import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.kryptokrauts.contraect.generated.AdventOfCodeDay11;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdventOfCodeDay11Test extends BaseTest {
	private static String contractId;

	@Test
	@Order(1)
	public void deploy() {
		AdventOfCodeDay11 adventOfCodeDay11 = new AdventOfCodeDay11(config,
				null);
		// deploy contract
		Pair<String, String> deployment = adventOfCodeDay11.deploy();
		String txHash = deployment.getValue0();
		contractId = deployment.getValue1();
		log.info("tx-hash of deployment: {}", txHash);
		log.info("contract id: {}", contractId);
		Assertions.assertNotNull(contractId);
	}

	@Test
	@Order(2)
	public void testSolve1() {
		AdventOfCodeDay11 adventOfCodeDay11 = new AdventOfCodeDay11(config,
				contractId);

		log.info("Solve1: {} ", adventOfCodeDay11.solve_1());
	}

	@Test
	@Order(3)
	public void testSolve2() {
		AdventOfCodeDay11 adventOfCodeDay11 = new AdventOfCodeDay11(config,
				contractId);

		log.info("Solve2: {} ", adventOfCodeDay11.solve_2());
	}
}
