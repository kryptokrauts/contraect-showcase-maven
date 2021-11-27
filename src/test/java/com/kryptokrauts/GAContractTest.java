package com.kryptokrauts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.ImmutableMap;
import com.kryptokrauts.aeternity.sdk.constants.BaseConstants;
import com.kryptokrauts.aeternity.sdk.domain.StringResultWrapper;
import com.kryptokrauts.aeternity.sdk.domain.secret.KeyPair;
import com.kryptokrauts.aeternity.sdk.service.account.domain.AccountResult;
import com.kryptokrauts.aeternity.sdk.service.info.domain.TransactionResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.ContractCallObjectModel;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.DryRunAccountModel;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.DryRunRequest;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.DryRunTransactionResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.DryRunTransactionResults;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.PostTransactionResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.AbstractTransactionModel;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.ContractCallTransactionModel;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.GeneralizedAccountsAttachTransactionModel;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.GeneralizedAccountsMetaTransactionModel;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.SpendTransactionModel;
import com.kryptokrauts.aeternity.sdk.util.ByteUtils;
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
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
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
public class GAContractTest extends BaseTest {

  private static int NUM_TRIALS_DEFAULT = 20;

  private static final String GA_CONTRACT_NAME = "GAMultiSig.aes";

  private static final int DEFAULT_TTL = 200;

  private KeyPair generalizedAccount;
  private List<KeyPair> signers;
  private KeyPair spendTxRecipient;
  private int numToSign = 3;
  private BigInteger amountToSpend;
  private String gaTxHash;
  private SpendTransactionModel spendTxModel;

  @Test
  public void testGASuccessCase() throws Throwable {
    try {
      /** First Signer proposes and automatically confirms */
      this.proposeSpendTransaction(gaTxHash, DEFAULT_TTL, signers.get(0));

      /** All other signers confirm */
      IntStream.range(1, numToSign).forEach(i -> this.confirmSigner(gaTxHash, signers.get(i)));

      /** Signer 2 calls Auth function (GAMeta tx) */
      this.callAuthFunction(gaTxHash, spendTxModel, signers.get(1));

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

      this.confirmSigner(
          gaTxHash,
          signers.get(1),
          r -> {
            assertEquals("revert", r.getContractCallObject().getReturnType());
            assertEquals(
                "{abort=[ERROR_TX_ALREADY_EXPIRED]}",
                decodeCallResult("confirm", r.getContractCallObject()).toString());
          });

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

      this.confirmSigner(
          gaTxHash,
          config.getKeyPair(),
          r -> {
            assertEquals("revert", r.getContractCallObject().getReturnType());
            assertEquals(
                "{abort=[ERROR_NOT_AUTHORIZED]}",
                decodeCallResult("confirm", r.getContractCallObject()).toString());
          });

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

      this.confirmSigner(
          gaTxHash,
          signers.get(0),
          r -> {
            assertEquals("revert", r.getContractCallObject().getReturnType());
            assertEquals(
                "{abort=[ERROR_ALREADY_CONFIRMED]}",
                decodeCallResult("confirm", r.getContractCallObject()).toString());
          });

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
          r -> {
            assertEquals("revert", r.getContractCallObject().getReturnType());
            assertEquals(
                "{abort=[ERROR_A_TX_IS_ALREADY_PROPOSED]}",
                decodeCallResult("propose", r.getContractCallObject()).toString());
          });

    } catch (Exception e) {
      log.error("Error testing generalized accounts spend tx success case: " + e);
      fail(e);
    }
  }

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

    BigInteger gas = BigInteger.valueOf(800000);
    BigInteger gasPrice = BigInteger.valueOf(BaseConstants.MINIMAL_GAS_PRICE);

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
            .gas(gas)
            .gasPrice(gasPrice)
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
            .gas(dryRunResult.getContractCallObject().getGasUsed())
            .gasPrice(dryRunResult.getContractCallObject().getGasPrice())
            .build();

    PostTransactionResult result =
        blockingPostTx(gaAttachTx, generalizedAccount.getEncodedPrivateKey());

    log.info("GA attach transaction result: {}", result);
  }

  /**
   * get the current contracts nonce
   *
   * @return
   */
  private String getContractNonce() {

    String callData =
        aeternityService
            .compiler
            .blockingEncodeCalldata(
                getContractCode(), "get_nonce", new LinkedList<>(), ImmutableMap.of())
            .getResult();
    assertNotNull(callData);

    ContractCallTransactionModel contractCallModel =
        ContractCallTransactionModel.builder()
            .callData(callData)
            .gas(BigInteger.valueOf(1579000l))
            .contractId(getGAContractAddress())
            .gasPrice(BigInteger.valueOf(BaseConstants.MINIMAL_GAS_PRICE))
            .amount(BigInteger.ZERO)
            .nonce(getNextNonce(config.getKeyPair().getAddress()))
            .callerId(config.getKeyPair().getAddress())
            .ttl(BigInteger.ZERO)
            .virtualMachine(config.getTargetVM())
            .build();

    DryRunTransactionResults dryRunResults =
        aeternityService.transactions.blockingDryRunTransactions(
            DryRunRequest.builder()
                .build()
                .transactionInputItem(contractCallModel)
                .account(
                    DryRunAccountModel.builder()
                        .publicKey(config.getKeyPair().getAddress())
                        .build()));
    DryRunTransactionResult dryRunResult = dryRunResults.getResults().get(0);

    Object resultObject = decodeCallResult("get_nonce", dryRunResult.getContractCallObject());

    log.debug("Current gaContract nonce is: {}", resultObject);

    return resultObject.toString();
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
   * @param gaTxHash
   * @param spendTxModel
   * @throws Throwable
   */
  private void callAuthFunction(String gaTxHash, SpendTransactionModel spendTxModel, KeyPair caller)
      throws Throwable {
    /** Call GAMeta tx */
    log.info("{} calls authentication function", caller.getAddress());

    List<String> paramList =
        List.of(getSignature(gaTxHash, generalizedAccount), getContractNonce());
    log.debug("Using parameters for call authorize: {}", paramList);

    String callData =
        aeternityService
            .compiler
            .blockingEncodeCalldata(
                getContractCode(), "authorize", paramList, Collections.emptyMap())
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
  private void confirmSigner(
      String gaTxHash, KeyPair signer, Consumer<DryRunTransactionResult> assertCheck) {
    try {
      log.info(
          "Calling confirm ga tx with parameters\nSigner: {}\nGA Tx hash: {}",
          signer.getAddress(),
          gaTxHash);

      List<String> params = Arrays.asList(getSignature(gaTxHash, signer), getContractNonce());

      String callData =
          aeternityService
              .compiler
              .blockingEncodeCalldata(getContractCode(), "confirm", params, ImmutableMap.of())
              .getResult();
      assertNotNull(callData);

      ContractCallTransactionModel contractCallModel =
          ContractCallTransactionModel.builder()
              .callData(callData)
              .contractId(getGAContractAddress())
              .gasPrice(BigInteger.valueOf(BaseConstants.MINIMAL_GAS_PRICE))
              .gas(BigInteger.valueOf(1000000))
              .nonce(getNextNonce(signer.getAddress()))
              .callerId(signer.getAddress())
              .ttl(BigInteger.ZERO)
              .build();

      DryRunTransactionResults dryRunResults =
          aeternityService.transactions.blockingDryRunTransactions(
              DryRunRequest.builder()
                  .build()
                  .transactionInputItem(contractCallModel)
                  .account(
                      DryRunAccountModel.builder()
                          .publicKey(config.getKeyPair().getAddress())
                          .build()));
      DryRunTransactionResult dryRunResult = dryRunResults.getResults().get(0);

      assertDryRunCallSuccessful(dryRunResults.getResults().get(0), "confirm", assertCheck);

      contractCallModel =
          contractCallModel
              .toBuilder()
              .gas(dryRunResult.getContractCallObject().getGasUsed())
              .build();

      blockingPostTx(contractCallModel, signer.getEncodedPrivateKey());

      log.info("Signer {} successfully confirmed proposed spend tx", signer.getAddress());
    } catch (Throwable e) {
      fail("Error confirming proposed tx for signer " + signer.getAddress(), e);
    }
  }

  private void proposeSpendTransaction(String gaTxHash, int relativeTtl, KeyPair keyPair)
      throws Throwable {
    this.proposeSpendTransaction(gaTxHash, relativeTtl, keyPair, null);
  }

  private void proposeSpendTransaction(
      String gaTxHash,
      int relativeTtl,
      KeyPair keyPair,
      Consumer<DryRunTransactionResult> assertCheck)
      throws Throwable {
    /** Call propose using the spend tx */
    log.info(
        "Calling propose spend tx with parameters\nGATxHash: {}\nTTL: {}", gaTxHash, relativeTtl);

    List<String> paramList =
        Arrays.asList(
            "#" + gaTxHash,
            "RelativeTTL(" + relativeTtl + ")",
            getSignature(gaTxHash, keyPair),
            getContractNonce());
    log.debug("Using parameters for call propose: {}", paramList);

    String callData =
        aeternityService
            .compiler
            .blockingEncodeCalldata(getContractCode(), "propose", paramList, Collections.emptyMap())
            .getResult();

    assertNotNull(callData);

    ContractCallTransactionModel proposeTx =
        ContractCallTransactionModel.builder()
            .contractId(getGAContractAddress())
            .callData(callData)
            .gas(BigInteger.valueOf(1000000))
            .gasPrice(BigInteger.valueOf(BaseConstants.MINIMAL_GAS_PRICE))
            .nonce(getNextNonce(keyPair.getAddress()))
            .callerId(keyPair.getAddress())
            .ttl(BigInteger.ZERO)
            .build();
    DryRunTransactionResults dryRunResults =
        aeternityService.transactions.blockingDryRunTransactions(
            DryRunRequest.builder()
                .build()
                .transactionInputItem(
                    aeternityService
                        .transactions
                        .blockingCreateUnsignedTransaction(proposeTx)
                        .getResult())
                .account(DryRunAccountModel.builder().publicKey(keyPair.getAddress()).build()));

    assertDryRunCallSuccessful(dryRunResults.getResults().get(0), "propose", assertCheck);

    ContractCallObjectModel contractCallObjectModel =
        dryRunResults.getResults().get(0).getContractCallObject();

    proposeTx =
        proposeTx
            .toBuilder()
            .gas(contractCallObjectModel.getGasUsed())
            .gasPrice(contractCallObjectModel.getGasPrice())
            .build();

    PostTransactionResult proposeResult =
        this.blockingPostTx(proposeTx, keyPair.getEncodedPrivateKey());

    log.info("Post transaction result of propose spendTx is: {}", proposeResult);
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
    byte[] networkDataWithAdditionalPrefix =
        (config.getNetwork().getId()).getBytes(StandardCharsets.UTF_8);
    byte[] txAndNetwork =
        ByteUtils.concatenate(
            networkDataWithAdditionalPrefix,
            EncodingUtils.decodeCheckWithIdentifier(unsignedSpendTx));

    String gaTxHash = new String(Hex.encode(EncodingUtils.hash(txAndNetwork)));

    log.info(
        "\nSpend transaction to send amount of {} aettos to recipient {}\nUnsigned Spend Tx: {}\nGATxHash: {}",
        amountToSpend,
        spendTxRecipient.getAddress(),
        unsignedSpendTx,
        gaTxHash);

    return Pair.with(gaTxHash, spendTxModel);
  }

  /** helper methods */
  private String getSignature(String hash, KeyPair keypair) throws Throwable {
    return "#" + Hex.toHexString(SigningUtil.sign(hash, keypair.getEncodedPrivateKey()));
  }

  private Object decodeCallResult(
      String function, ContractCallObjectModel contractCallObjectModel) {
    return aeternityService
        .compiler
        .blockingDecodeCallResult(
            getContractCode(),
            function,
            contractCallObjectModel.getReturnType(),
            contractCallObjectModel.getReturnValue(),
            null)
        .getResult();
  }

  private void assertDryRunCallSuccessful(
      DryRunTransactionResult dryRunResult,
      String method,
      Consumer<DryRunTransactionResult> assertCheck) {
    assertEquals("ok", dryRunResult.getResult());
    if (assertCheck == null) {
      assertEquals(
          "ok",
          dryRunResult.getContractCallObject().getReturnType(),
          decodeCallResult(method, dryRunResult.getContractCallObject()).toString());
    } else {
      assertCheck.accept(dryRunResult);
    }
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
            .ttl(BigInteger.ZERO)
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