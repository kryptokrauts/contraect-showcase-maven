package com.kryptokrauts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kryptokrauts.aeternity.sdk.constants.AENS;
import com.kryptokrauts.aeternity.sdk.service.ServiceConfiguration;
import com.kryptokrauts.aeternity.sdk.service.delegation.DelegationService;
import com.kryptokrauts.aeternity.sdk.service.delegation.DelegationServiceFactory;
import com.kryptokrauts.aeternity.sdk.util.CryptoUtils;
import com.kryptokrauts.contraect.generated.AENSUpdate;
import com.kryptokrauts.contraect.generated.AENSUpdate.Address;
import com.kryptokrauts.contraect.generated.AENSUpdate.Hash;
import com.kryptokrauts.contraect.generated.AENSUpdate.Pointee;
import com.kryptokrauts.contraect.generated.AENSUpdate.Pointee.PointeeType;
import com.kryptokrauts.contraect.generated.AENSUpdate.Signature;
import java.math.BigInteger;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
public class AENSUpdateTest extends BaseTest {

  private static AENSUpdate contract;

  private static String delegationTestName;

  private static Signature aensDelegationSignature;

  /** PreClaim and Claim Name */
  @BeforeAll
  private static void deployContract() {
    contract = new AENSUpdate(config, null);
    Pair<String, String> contractId = contract.deploy();
    log.info("Deployed contract: {}", contractId);

    delegationTestName = "delegationTestName" + new Random().nextInt() + ".chain";
    BigInteger salt = CryptoUtils.generateNamespaceSalt();

    DelegationService nameOwnerDelegationService =
        new DelegationServiceFactory()
            .getService(
                ServiceConfiguration.configure()
                    .network(config.getNetwork())
                    .keyPair(baseKeyPair)
                    .compile());

    Hash commitmentHash =
        new Hash(nameOwnerDelegationService.getAensCommitmentHash(delegationTestName, salt));
    Signature preClaimSignature =
        new Signature(
            nameOwnerDelegationService.createAensDelegationSignature(contract.getContractId()));

    contract.preclaim(new Address(baseKeyPair.getAddress()), commitmentHash, preClaimSignature);

    aensDelegationSignature =
        new Signature(
            nameOwnerDelegationService.createAensDelegationSignature(
                contract.getContractId(), delegationTestName));

    contract.claim(
        new Address(baseKeyPair.getAddress()),
        delegationTestName,
        salt,
        AENS.getInitialNameFee(delegationTestName),
        aensDelegationSignature);
  }

  @Test
  public void testAENSContractInteraction() throws Exception {

    /** Set pointer to all four types */
    contract.update_name(
        new Address(baseKeyPair.getAddress()), delegationTestName, aensDelegationSignature);

    assertEquals(4, contract.get_aens(delegationTestName).get().getPointers().size());

    Address pointerAddress = new Address(keyPairService.generateKeyPair().getAddress());
    Pointee pointee = new Pointee(pointerAddress, PointeeType.ContractPt);

    /** update a single pointer */
    contract.updatePointee(
        new Address(baseKeyPair.getAddress()),
        delegationTestName,
        "myKey",
        pointee,
        aensDelegationSignature);

    assertEquals(
        pointerAddress,
        contract.get_aens(delegationTestName).get().getPointers().get("myKey").getAddress());

    assertEquals(pointee, contract.get_pointee(delegationTestName, "myKey").get());
  }
}
