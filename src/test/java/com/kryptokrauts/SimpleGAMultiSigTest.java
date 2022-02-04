package com.kryptokrauts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.kryptokrauts.aeternity.sdk.domain.StringResultWrapper;
import com.kryptokrauts.aeternity.sdk.domain.secret.KeyPair;
import com.kryptokrauts.aeternity.sdk.domain.sophia.SophiaChainTTL;
import com.kryptokrauts.aeternity.sdk.domain.sophia.SophiaChainTTL.Type;
import com.kryptokrauts.aeternity.sdk.domain.sophia.SophiaHash;
import com.kryptokrauts.aeternity.sdk.domain.sophia.SophiaSignature;
import com.kryptokrauts.aeternity.sdk.service.account.domain.AccountResult;
import com.kryptokrauts.aeternity.sdk.service.info.domain.TransactionResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.ContractTxOptions;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.ContractTxResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.DryRunAccountModel;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.DryRunRequest;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.DryRunTransactionResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.DryRunTransactionResults;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.PostTransactionResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.AbstractTransactionModel;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.GeneralizedAccountsAttachTransactionModel;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.GeneralizedAccountsMetaTransactionModel;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.SpendTransactionModel;
import com.kryptokrauts.aeternity.sdk.util.EncodingUtils;
import com.kryptokrauts.aeternity.sdk.util.SigningUtil;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.encoders.Hex;
import org.javatuples.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
public class SimpleGAMultiSigTest extends BaseTest {

  private static int NUM_TRIALS_DEFAULT = 20;

  private static final String GA_CONTRACT_NAME = "SimpleGAMultiSig.aes";

  private static final int DEFAULT_TTL = 200;

  private KeyPair generalizedAccount;
  private List<KeyPair> signers;
  private KeyPair spendTxRecipient;
  private int numToSign = 3;
  private BigInteger amountToSpend;
  private String gaTxHash;
  private SpendTransactionModel spendTxModel;

  @BeforeEach
  public void prepareForTest() {
    try {
      /** fund the signers */
      fundSigners();
      /** attach contract to generalized account */
      attachGA();
      /** define recipient and amount */
      amountToSpend = unitConversionService18Decimals.toSmallestUnit(BigDecimal.ONE);
      spendTxRecipient = keyPairService.generateKeyPair();
      log.debug(
          "Recipients ammount before ga spend transaction is: {}",
          getAccount(spendTxRecipient.getAddress()));
      /** prepare GA Tx */
      Pair<String, SpendTransactionModel> tx =
          this.createUnsignedGATx(spendTxRecipient, amountToSpend);
      gaTxHash = tx.getValue0();
      spendTxModel = tx.getValue1();
    } catch (Throwable e) {
      log.error("Error preparing test", e);
      fail(e);
    }
  }

  @Test
  public void testGASuccessCase() throws Throwable {
    try {
      /** First Signer proposes and automatically confirms */
      this.proposeSpendTransaction(gaTxHash, DEFAULT_TTL, signers.get(0));

      /** All other signers confirm */
      IntStream.range(1, numToSign).forEach(i -> this.confirmSigner(gaTxHash, signers.get(i)));

      /** Signer 2 calls Auth function (GAMeta tx) */
      this.callAuthFunction(spendTxModel, signers.get(1));

      /** check recipients balance */
      assertEquals(amountToSpend, getAccount(spendTxRecipient.getAddress()).getBalance());
    } catch (Exception e) {
      log.error("Error testing generalized accounts spend tx success case: " + e);
      fail(e);
    }
  }

  @Test
  public void testGAFailProposedTxExpired() throws Throwable {
    try {
      /** First Signer proposes and automatically confirms */
      this.proposeSpendTransaction(gaTxHash, 5, signers.get(0));

      this.confirmSigner(gaTxHash, signers.get(1), "{\"abort\":[\"ERROR_TX_ALREADY_EXPIRED\"]}");

    } catch (Exception e) {
      log.error("Error testing generalized accounts spend tx success case: " + e);
      fail(e);
    }
  }

  @Test
  public void testGAFailConfirmNotAuthorized() throws Throwable {
    try {
      /** First Signer proposes and automatically confirms */
      this.proposeSpendTransaction(gaTxHash, DEFAULT_TTL, signers.get(0));

      this.confirmSigner(gaTxHash, config.getKeyPair(), "{\"abort\":[\"ERROR_NOT_AUTHORIZED\"]}");

    } catch (Exception e) {
      log.error("Error testing generalized accounts spend tx success case: " + e);
      fail(e);
    }
  }

  @Test
  public void testGAFailAlreadyConfirmed() throws Throwable {
    try {
      /** First Signer proposes and automatically confirms */
      this.proposeSpendTransaction(gaTxHash, DEFAULT_TTL, signers.get(0));

      this.confirmSigner(gaTxHash, signers.get(0), "{\"abort\":[\"ERROR_ALREADY_CONFIRMED\"]}");

    } catch (Exception e) {
      log.error("Error testing generalized accounts spend tx success case: " + e);
      fail(e);
    }
  }

  @Test
  public void testGAFailAlreadyProposed() throws Throwable {
    try {
      /** First Signer proposes and automatically confirms */
      this.proposeSpendTransaction(gaTxHash, DEFAULT_TTL, signers.get(0));

      this.proposeSpendTransaction(
          gaTxHash,
          DEFAULT_TTL,
          signers.get(0),
          "{\"abort\":[\"ERROR_A_TX_IS_ALREADY_PROPOSED\"]}");

    } catch (Exception e) {
      log.error("Error testing generalized accounts spend tx success case: " + e);
      fail(e);
    }
  }

  /**
   * fund signers
   *
   * @throws Throwable
   */
  private void fundSigners() throws Throwable {
    generalizedAccount = keyPairService.generateKeyPair();
    log.debug("Generalized account is: {}", generalizedAccount);
    fundAddress(
        generalizedAccount.getAddress(),
        unitConversionService18Decimals.toSmallestUnit(BigDecimal.TEN.multiply(BigDecimal.TEN)));
    log.debug(
        "Balance of generalized account is: {}",
        aeternityService.accounts.blockingGetAccount(generalizedAccount.getAddress()).getBalance());

    signers = new ArrayList<>();

    IntStream.range(0, numToSign)
        .forEach(
            i -> {
              KeyPair signer = keyPairService.generateKeyPair();
              log.debug("Signer {} is: {}", i, signer);
              fundAddress(
                  signer.getAddress(),
                  unitConversionService18Decimals.toSmallestUnit(BigDecimal.ONE));
              log.debug(
                  "Balance of Signer {} is: {}",
                  i,
                  aeternityService.accounts.blockingGetAccount(signer.getAddress()).getBalance());
              signers.add(signer);
            });
  }

  /**
   * attach contract to generalized account
   *
   * @throws Throwable
   */
  private void attachGA() throws Throwable {
    log.info("Creating ga attach transaction");
    AccountResult gaAccountResult =
        aeternityService.accounts.blockingGetAccount(generalizedAccount.getAddress());

    String signer_addresses =
        "[" + signers.stream().map(s -> s.getAddress()).collect(Collectors.joining(",")) + "]";

    String callData =
        aeternityService
            .compiler
            .blockingEncodeCalldata(
                getContractCode(),
                "init",
                Arrays.asList(String.valueOf(numToSign), signer_addresses),
                Collections.emptyMap())
            .getResult();

    assertNotNull(callData);

    GeneralizedAccountsAttachTransactionModel gaAttachTx =
        GeneralizedAccountsAttachTransactionModel.builder()
            .authFun(EncodingUtils.generateAuthFunHash("authorize"))
            .callData(callData)
            .code(getCode())
            .nonce(getNextNonce(generalizedAccount.getAddress()))
            .ownerId(gaAccountResult.getPublicKey())
            .build();

    String unsignedTx =
        aeternityService.transactions.blockingCreateUnsignedTransaction(gaAttachTx).getResult();

    DryRunTransactionResults dryRunResults =
        aeternityService.transactions.blockingDryRunTransactions(
            DryRunRequest.builder()
                .build()
                .account(
                    DryRunAccountModel.builder().publicKey(gaAccountResult.getPublicKey()).build())
                .transactionInputItem(unsignedTx));
    DryRunTransactionResult dryRunResult = dryRunResults.getResults().get(0);

    gaAttachTx =
        gaAttachTx
            .toBuilder()
            .gasLimit(dryRunResult.getContractCallObject().getGasUsed())
            .gasPrice(dryRunResult.getContractCallObject().getGasPrice())
            .build();

    PostTransactionResult result =
        blockingPostTx(gaAttachTx, generalizedAccount.getEncodedPrivateKey());

    log.info("GA attach transaction result: {}", result);
  }

  private void logConsensusInfo() {
    Object resultObject = aeternityService.transactions
        .blockingReadOnlyContractCall(getGAContractAddress(), "get_consensus_info",
            getContractCode());
    log.info("Consensus Info: {}", resultObject.toString());
  }

  /**
   * get the contract address of the GA account
   *
   * @return
   */
  private String getGAContractAddress() {
    return aeternityService
        .accounts
        .blockingGetAccount(generalizedAccount.getAddress())
        .getGaContractId();
  }

  /**
   * use the GA Meta function calling contracts auth entrypoint
   *
   * @param spendTxModel
   * @throws Throwable
   */
  private void callAuthFunction(SpendTransactionModel spendTxModel, KeyPair caller) {
    /** Call GAMeta tx */
    log.info("{} calls authentication function", caller.getAddress());

    String callData =
        aeternityService
            .compiler
            .blockingEncodeCalldata(
                getContractCode(), "authorize", null, Collections.emptyMap())
            .getResult();

    assertNotNull(callData);

    try {
      GeneralizedAccountsMetaTransactionModel gaMetaTx =
          GeneralizedAccountsMetaTransactionModel.builder()
              .gaId(generalizedAccount.getAddress())
              .authData(callData)
              .innerTxModel(spendTxModel)
              .build();

      PostTransactionResult result =
          aeternityService.transactions.blockingPostTransaction(
              gaMetaTx, caller.getEncodedPrivateKey());
      log.info("gaMetaTx result: {}", result);
      logConsensusInfo();
    } catch (Throwable t) {
      log.error("Error calling gaMetaTx: ", t);
      fail(t);
    }
  }

  private void confirmSigner(String gaTxHash, KeyPair signer) {
    confirmSigner(gaTxHash, signer, null);
  }

  /**
   * Confirm proposed tx
   *
   * @param gaTxHash
   * @param signer
   * @throws Throwable
   */
  private void confirmSigner(String gaTxHash, KeyPair signer, String expectedErrorMessage) {
    try {
      log.info(
          "Calling confirm ga tx with parameters\nSigner: {}\nGA Tx hash: {}",
          signer.getAddress(),
          gaTxHash);

      List<Object> params = Arrays.asList(getSignature(gaTxHash, signer));
      ContractTxResult txResult =
          aeternityService.transactions.blockingStatefulContractCall(
              getGAContractAddress(),
              "confirm",
              getContractCode(),
              ContractTxOptions.builder().params(params).customKeyPair(signer).build());
      log.info("ContractTxResult: " + txResult);
      if (expectedErrorMessage != null) {
        assertEquals("revert", txResult.getCallResult().getReturnType());
        assertEquals(expectedErrorMessage, txResult.getDecodedValue().toString());
      } else {
        assertEquals("ok", txResult.getCallResult().getReturnType());
        log.info("Signer {} successfully confirmed proposed spend tx", signer.getAddress());
        logConsensusInfo();
      }
    } catch (Throwable e) {
      fail("Error confirming proposed tx for signer " + signer.getAddress(), e);
    }
  }

  private void proposeSpendTransaction(String gaTxHash, int relativeTtl, KeyPair keyPair)
      throws Throwable {
    this.proposeSpendTransaction(gaTxHash, relativeTtl, keyPair, null);
  }

  private void proposeSpendTransaction(
      String gaTxHash, int relativeTtl, KeyPair keyPair, String expectedErrorMessage)
      throws Throwable {
    /** Call propose using the spend tx */
    log.info(
        "Calling propose spendTx with parameters\nGATxHash: {}\nTTL: {}", gaTxHash, relativeTtl);

    ContractTxResult txResult =
        aeternityService.transactions.blockingStatefulContractCall(
            getGAContractAddress(),
            "propose",
            getContractCode(),
            ContractTxOptions.builder()
                .params(
                    List.of(
                        new SophiaHash(gaTxHash),
                        new SophiaChainTTL(BigInteger.valueOf(relativeTtl), Type.RelativeTTL),
                        new SophiaSignature(getSignature(gaTxHash, keyPair))))
                .customKeyPair(keyPair)
                .build());
    log.info("Tx result of propose spendTx is: {}", txResult);

    if (expectedErrorMessage != null) {
      assertEquals("revert", txResult.getCallResult().getReturnType());
      assertEquals(expectedErrorMessage, txResult.getDecodedValue().toString());
    } else {
      assertEquals("ok", txResult.getCallResult().getReturnType());
      log.info("Signer {} successfully proposed spend tx", keyPair.getAddress());
      logConsensusInfo();
    }
  }

  /**
   * creates the GA tx
   *
   * @param recipient
   * @param amountToSpend
   * @return Pair of GA tx hash and the original unsigned spend tx
   */
  private Pair<String, SpendTransactionModel> createUnsignedGATx(
      KeyPair recipient, BigInteger amountToSpend) {
    // create the unsigned spend transaction
    SpendTransactionModel spendTxModel =
        SpendTransactionModel.builder()
            .sender(generalizedAccount.getAddress())
            .recipient(recipient.getAddress())
            .amount(amountToSpend)
            .payload("Spend this amount using a multisigned ga account")
            .nonce(BigInteger.ZERO)
            .build();

    String unsignedSpendTx =
        aeternityService.transactions.blockingCreateUnsignedTransaction(spendTxModel).getResult();

    // create the ga transaction hash with the spendTx as payload
    String gaTxHash = aeternityService.transactions.computeGAInnerTxHash(spendTxModel);

    log.info(
        "\nSpend transaction to send amount of {} aettos to recipient {}\nUnsigned Spend Tx: {}\nGATxHash: {}",
        amountToSpend,
        spendTxRecipient.getAddress(),
        unsignedSpendTx,
        gaTxHash);

    return Pair.with(gaTxHash, spendTxModel);
  }

  /**
   * helper methods
   */
  private String getSignature(String hash, KeyPair keypair) throws Throwable {
    return "#" + Hex.toHexString(SigningUtil.sign(hash, keypair.getEncodedPrivateKey()));
  }

  private String getCode() throws Exception {
    StringResultWrapper resultWrapper =
        aeternityService.compiler.blockingCompile(getContractCode(), null, null);
    return resultWrapper.getResult();
  }

  private String getContractCode() {
    try {
      return IOUtils.toString(
          Paths.get("src/test/resources/contraects", GA_CONTRACT_NAME).toUri(),
          StandardCharsets.UTF_8.toString());
    } catch (IOException e) {
      log.error("Cannot read contract");
      return null;
    }
  }

  /**
   * fund address with amount
   *
   * @param recipient
   * @param amount
   * @throws Throwable
   */
  protected void fundAddress(String recipient, BigInteger amount) {
    log.debug("Spending amount of {} to recipient {}", amount, recipient);
    SpendTransactionModel spendTx =
        SpendTransactionModel.builder()
            .amount(amount)
            .sender(baseKeyPair.getAddress())
            .recipient(recipient)
            .nonce(getNextNonce(baseKeyPair.getAddress()))
            .build();
    try {
      blockingPostTx(spendTx);
    } catch (Throwable e) {
      fail("Failed funding signer " + recipient, e);
    }
    log.info("Spending amount of {} to recipient {} successful", amount, recipient);
  }

  /**
   * get next nonce for address
   *
   * @param address
   * @return
   */
  protected BigInteger getNextNonce(String address) {
    return getAccount(address).getNonce().add(BigInteger.ONE);
  }

  protected AccountResult getAccount(String address) {
    try {
      if (address == null) {
        return aeternityService.accounts.blockingGetAccount();
      }
      return aeternityService.accounts.blockingGetAccount(address);
    } catch (Exception notFunded) {
      return AccountResult.builder()
          .balance(BigInteger.ZERO)
          .publicKey(address)
          .rootErrorMessage("Account not funded yet")
          .build();
    }
  }

  protected PostTransactionResult blockingPostTx(AbstractTransactionModel<?> tx) throws Throwable {
    return blockingPostTx(tx, null);
  }

  protected PostTransactionResult blockingPostTx(AbstractTransactionModel<?> tx, String privateKey)
      throws Throwable {
    if (privateKey == null) {
      privateKey = baseKeyPair.getEncodedPrivateKey();
    }
    PostTransactionResult postTxResponse =
        aeternityService.transactions.blockingPostTransaction(tx, privateKey);
    log.debug("PostTx hash: " + postTxResponse.getTxHash());
    TransactionResult txValue = waitForTxMined(postTxResponse.getTxHash());
    log.debug(
        String.format(
            "Transaction of type %s is mined at block %s with height %s",
            txValue.getTxType(), txValue.getBlockHash(), txValue.getBlockHeight()));

    return postTxResponse;
  }

  protected TransactionResult waitForTxMined(String txHash) throws Throwable {
    int blockHeight = -1;
    TransactionResult minedTx = null;
    int doneTrials = 1;

    while (blockHeight == -1 && doneTrials < NUM_TRIALS_DEFAULT) {
      minedTx =
          callMethodAndGetResult(
              () -> aeternityService.info.asyncGetTransactionByHash(txHash),
              TransactionResult.class);
      if (minedTx.getBlockHeight().intValue() > 1) {
        log.debug("Mined tx: " + minedTx);
        blockHeight = minedTx.getBlockHeight().intValue();
      } else {
        log.debug(
            String.format(
                "Transaction not mined yet, trying again in 1 second (%s of %s)...",
                doneTrials, NUM_TRIALS_DEFAULT));
        Thread.sleep(1000);
        doneTrials++;
      }
    }

    if (blockHeight == -1) {
      throw new InterruptedException(
          String.format(
              "Transaction %s was not mined after %s trials, aborting", txHash, doneTrials));
    }

    return minedTx;
  }

  protected <T> T callMethodAndGetResult(Supplier<Single<T>> observerMethod, Class<T> type)
      throws Throwable {
    return callMethodAndGetResult(NUM_TRIALS_DEFAULT, observerMethod, type, false);
  }

  protected <T> T callMethodAndGetResult(
      Integer numTrials, Supplier<Single<T>> observerMethod, Class<T> type, boolean awaitException)
      throws Throwable {

    if (numTrials == null) {
      numTrials = NUM_TRIALS_DEFAULT;
    }

    int doneTrials = 1;
    T result = null;

    do {
      Single<T> resultSingle = observerMethod.get();
      TestObserver<T> singleTestObserver = resultSingle.test();
      singleTestObserver.awaitTerminalEvent();
      if (singleTestObserver.errorCount() > 0) {
        if (awaitException) {
          throw singleTestObserver.errors().get(0);
        }
        if (doneTrials == numTrials) {
          log.error("Following error(s) occured while waiting for result of call, aborting");
          for (Throwable error : singleTestObserver.errors()) {
            log.error(error.toString());
          }
          throw new InterruptedException("Max number of function call trials exceeded, aborting");
        }
        log.debug(
            String.format(
                "Unable to receive object of type %s, trying again in 1 second (%s of %s)...",
                type.getSimpleName(), doneTrials, numTrials));
        Thread.sleep(1000);
        doneTrials++;
      } else {
        if (!awaitException) {
          result = singleTestObserver.values().get(0);
        } else {
          log.debug(
              String.format(
                  "Waiting for exception, trying again in 1 second (%s of %s)...",
                  doneTrials, numTrials));
          Thread.sleep(1000);
          doneTrials++;
        }
      }
    } while (result == null);

    return result;
  }
}
