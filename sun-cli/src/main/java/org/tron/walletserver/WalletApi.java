package org.tron.walletserver;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;
import org.tron.api.GrpcAPI;
import org.tron.api.GrpcAPI.AccountNetMessage;
import org.tron.api.GrpcAPI.AccountResourceMessage;
import org.tron.api.GrpcAPI.AddressPrKeyPairMessage;
import org.tron.api.GrpcAPI.AssetIssueList;
import org.tron.api.GrpcAPI.BlockExtention;
import org.tron.api.GrpcAPI.BlockList;
import org.tron.api.GrpcAPI.BlockListExtention;
import org.tron.api.GrpcAPI.DelegatedResourceList;
import org.tron.api.GrpcAPI.EasyTransferResponse;
import org.tron.api.GrpcAPI.EmptyMessage;
import org.tron.api.GrpcAPI.ExchangeList;
import org.tron.api.GrpcAPI.NodeList;
import org.tron.api.GrpcAPI.ProposalList;
import org.tron.api.GrpcAPI.Return;
import org.tron.api.GrpcAPI.SideChainProposalList;
import org.tron.api.GrpcAPI.TransactionApprovedList;
import org.tron.api.GrpcAPI.TransactionExtention;
import org.tron.api.GrpcAPI.TransactionList;
import org.tron.api.GrpcAPI.TransactionListExtention;
import org.tron.api.GrpcAPI.TransactionSignWeight;
import org.tron.api.GrpcAPI.TransactionSignWeight.Result.response_code;
import org.tron.api.GrpcAPI.WitnessList;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.Hash;
import org.tron.common.crypto.Sha256Hash;
import org.tron.common.utils.AbiUtil;
import org.tron.common.utils.Base58;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.ByteUtil;
import org.tron.common.utils.DataWord;
import org.tron.common.utils.MUtil;
import org.tron.common.utils.TransactionUtils;
import org.tron.common.utils.Utils;
import org.tron.core.config.Configuration;
import org.tron.core.config.Parameter.CommonConstant;
import org.tron.core.exception.CancelException;
import org.tron.core.exception.CipherException;
import org.tron.core.exception.EncodingException;
import org.tron.keystore.CheckStrength;
import org.tron.keystore.Credentials;
import org.tron.keystore.Wallet;
import org.tron.keystore.WalletFile;
import org.tron.keystore.WalletUtils;
import org.tron.protos.Contract;
import org.tron.protos.Contract.AssetIssueContract;
import org.tron.protos.Contract.BuyStorageBytesContract;
import org.tron.protos.Contract.BuyStorageContract;
import org.tron.protos.Contract.CreateSmartContract;
import org.tron.protos.Contract.FreezeBalanceContract;
import org.tron.protos.Contract.SellStorageContract;
import org.tron.protos.Contract.UnfreezeAssetContract;
import org.tron.protos.Contract.UnfreezeBalanceContract;
import org.tron.protos.Contract.UpdateEnergyLimitContract;
import org.tron.protos.Contract.UpdateSettingContract;
import org.tron.protos.Contract.WithdrawBalanceContract;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.ChainParameters;
import org.tron.protos.Protocol.DelegatedResourceAccountIndex;
import org.tron.protos.Protocol.Exchange;
import org.tron.protos.Protocol.Key;
import org.tron.protos.Protocol.Permission;
import org.tron.protos.Protocol.Proposal;
import org.tron.protos.Protocol.SmartContract;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Protocol.Transaction.Result;
import org.tron.protos.Protocol.TransactionInfo;
import org.tron.protos.Protocol.TransactionSign;
import org.tron.protos.Protocol.Witness;


public class WalletApi {

  private static final Logger logger = LoggerFactory.getLogger("WalletApi");
  private static final String FilePath = "Wallet";
  private List<WalletFile> walletFile = new ArrayList<>();
  private boolean loginState = false;
  private byte[] address;
  private static byte addressPreFixByte = CommonConstant.ADD_PRE_FIX_BYTE_TESTNET;
  private static int rpcVersion = 0;
  private static int sideRpcVersion = 0;

  private static byte[] sideGatewayAddress;
  private static byte[] mainGatewayAddress;

  private static List<byte[]> mainChainGateWayList;

  private static GrpcClient rpcMain = initMain();
  private static GrpcClient rpcSide = initSide();
  private static GrpcClient rpcCli = rpcMain;

//  static {
//    new Timer().schedule(new TimerTask() {
//      @Override
//      public void run() {
//        String fullnode = selectFullNode();
//        if(!"".equals(fullnode)) {
//          rpcCli = new GrpcClient(fullnode);
//        }
//      }
//    }, 3 * 60 * 1000, 3 * 60 * 1000);
//  }

  public static GrpcClient initMain() {
    Config config = Configuration.getByPath("config.conf");

    String fullNode = "";
    String solidityNode = "";
    if (config.hasPath("mainchain.soliditynode.ip.list")) {
      solidityNode = config.getStringList("mainchain.soliditynode.ip.list").get(0);
    }
    if (config.hasPath("mainchain.fullnode.ip.list")) {
      fullNode = config.getStringList("mainchain.fullnode.ip.list").get(0);
    }
    if (config.hasPath("mainchain.net.type") && "mainnet"
      .equalsIgnoreCase(config.getString("mainchain.net.type"))) {
      WalletApi.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    } else {
      WalletApi.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_TESTNET);
    }
    if (config.hasPath("mainchain.RPC_version")) {
      rpcVersion = config.getInt("mainchain.RPC_version");
    }

    if (config.hasPath("mainchain.gateway_address")) {
      String temp = config.getString("mainchain.gateway_address");

      if (!WalletApi.addressValid(decodeFromBase58Check(temp))) {
        throw new RuntimeException("invalid main gateway address.");
      }

      mainGatewayAddress = WalletApi.decodeFromBase58Check(temp);
    }

    return new GrpcClient(fullNode, solidityNode);
  }

  private static List<byte[]> getGateWayList(final Config config, String path) {
    if (!config.hasPath(path)) {
      return Collections.emptyList();
    }
    List<byte[]> ret = new ArrayList<>();
    List<String> list = config.getStringList(path);
    for (String configString : list) {
      ret.add(WalletApi.decodeFromBase58Check(configString));
    }
    return ret;
  }

  public static GrpcClient initSide() {
    Config config = Configuration.getByPath("config.conf");

    String fullNode = "";
    String solidityNode = "";
    if (config.hasPath("sidechain.soliditynode.ip.list")) {
      solidityNode = config.getStringList("sidechain.soliditynode.ip.list").get(0);
    }
    if (config.hasPath("sidechain.fullnode.ip.list")) {
      fullNode = config.getStringList("sidechain.fullnode.ip.list").get(0);
    }
    if (config.hasPath("sidechain.net.type") && "mainnet"
      .equalsIgnoreCase(config.getString("sidechain.net.type"))) {
      WalletApi.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    } else {
      WalletApi.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_TESTNET);
    }
    if (config.hasPath("sidechain.RPC_version")) {
      sideRpcVersion = config.getInt("sidechain.RPC_version");
    }

    if (config.hasPath("sidechain.gateway_address")) {
      String temp = config.getString("sidechain.gateway_address");

      if (!WalletApi.addressValid(decodeFromBase58Check(temp))) {
        throw new RuntimeException("invalid side gateway address.");
      }

      sideGatewayAddress = WalletApi.decodeFromBase58Check(temp);
    }

    mainChainGateWayList = getGateWayList(config, "sidechain.mainChainGateWayList");

    return new GrpcClient(fullNode, solidityNode);
  }

  public static boolean isMainChain() {
    return rpcCli == rpcMain;
  }

  public static void switch2Main() {
    rpcCli = rpcMain;
  }

  public static void switch2Side() {
    rpcCli = rpcSide;
  }

  public static byte[] getSideGatewayAddress() {
    return sideGatewayAddress;
  }

  public static String selectFullNode() {
    Map<String, String> witnessMap = new HashMap<>();
    Config config = Configuration.getByPath("config.conf");
    List list = config.getObjectList("witnesses.witnessList");
    for (int i = 0; i < list.size(); i++) {
      ConfigObject obj = (ConfigObject) list.get(i);
      String ip = obj.get("ip").unwrapped().toString();
      String url = obj.get("url").unwrapped().toString();
      witnessMap.put(url, ip);
    }

    Optional<WitnessList> result = rpcCli.listWitnesses();
    long minMissedNum = 100000000L;
    String minMissedWitness = "";
    if (result.isPresent()) {
      List<Witness> witnessList = result.get().getWitnessesList();
      for (Witness witness : witnessList) {
        String url = witness.getUrl();
        long missedBlocks = witness.getTotalMissed();
        if (missedBlocks < minMissedNum) {
          minMissedNum = missedBlocks;
          minMissedWitness = url;
        }
      }
    }
    if (witnessMap.containsKey(minMissedWitness)) {
      return witnessMap.get(minMissedWitness);
    } else {
      return "";
    }
  }

  public static byte getAddressPreFixByte() {
    return addressPreFixByte;
  }

  public static void setAddressPreFixByte(byte addressPreFixByte) {
    WalletApi.addressPreFixByte = addressPreFixByte;
  }

  public static int getRpcVersion() {
    return rpcVersion;
  }

  /**
   * Creates a new WalletApi with a random ECKey or no ECKey.
   */
  public static WalletFile CreateWalletFile(byte[] password) throws CipherException {
    ECKey ecKey = new ECKey(Utils.getRandom());
    WalletFile walletFile = Wallet.createStandard(password, ecKey);
    return walletFile;
  }

  //  Create Wallet with a pritKey
  public static WalletFile CreateWalletFile(byte[] password, byte[] priKey) throws CipherException {
    ECKey ecKey = ECKey.fromPrivate(priKey);
    WalletFile walletFile = Wallet.createStandard(password, ecKey);
    return walletFile;
  }

  public boolean isLoginState() {
    return loginState;
  }

  public void logout() {
    loginState = false;
    walletFile.clear();
    this.walletFile = null;
  }

  public void setLogin() {
    loginState = true;
  }

  public boolean checkPassword(byte[] passwd) throws CipherException {
    return Wallet.validPassword(passwd, this.walletFile.get(0));
  }

  /**
   * Creates a Wallet with an existing ECKey.
   */
  public WalletApi(WalletFile walletFile) {
    if (this.walletFile.isEmpty()) {
      this.walletFile.add(walletFile);
    } else {
      this.walletFile.set(0, walletFile);
    }
    this.address = decodeFromBase58Check(walletFile.getAddress());
  }

  public ECKey getEcKey(WalletFile walletFile, byte[] password) throws CipherException {
    return Wallet.decrypt(password, walletFile);
  }

  public byte[] getPrivateBytes(byte[] password) throws CipherException, IOException {
    WalletFile walletFile = loadWalletFile();
    return Wallet.decrypt2PrivateBytes(password, walletFile);
  }

  public byte[] getAddress() {
    return address;
  }

  public static String store2Keystore(WalletFile walletFile) throws IOException {
    if (walletFile == null) {
      logger.warn("Warning: Store wallet failed, walletFile is null !!");
      return null;
    }
    File file = new File(FilePath);
    if (!file.exists()) {
      if (!file.mkdir()) {
        throw new IOException("Make directory failed!");
      }
    } else {
      if (!file.isDirectory()) {
        if (file.delete()) {
          if (!file.mkdir()) {
            throw new IOException("Make directory failed!");
          }
        } else {
          throw new IOException("File exists and can not be deleted!");
        }
      }
    }
    return WalletUtils.generateWalletFile(walletFile, file);
  }

  public static File selcetWalletFile() {
    File file = new File(FilePath);
    if (!file.exists() || !file.isDirectory()) {
      return null;
    }

    File[] wallets = file.listFiles();
    if (ArrayUtils.isEmpty(wallets)) {
      return null;
    }

    File wallet;
    if (wallets.length > 1) {
      for (int i = 0; i < wallets.length; i++) {
        System.out.println("The " + (i + 1) + "th keystore file name is " + wallets[i].getName());
      }
      System.out.println("Please choose between 1 and " + wallets.length);
      Scanner in = new Scanner(System.in);
      while (true) {
        String input = in.nextLine().trim();
        String num = input.split("\\s+")[0];
        int n;
        try {
          n = new Integer(num);
        } catch (NumberFormatException e) {
          System.out.println("Invaild number of " + num);
          System.out.println("Please choose again between 1 and " + wallets.length);
          continue;
        }
        if (n < 1 || n > wallets.length) {
          System.out.println("Please choose again between 1 and " + wallets.length);
          continue;
        }
        wallet = wallets[n - 1];
        break;
      }
    } else {
      wallet = wallets[0];
    }

    return wallet;
  }

  public WalletFile selcetWalletFileE() throws IOException {
    File file = selcetWalletFile();
    if (file == null) {
      throw new IOException(
        "No keystore file found, please use registerwallet or importwallet first!");
    }
    String name = file.getName();
    for (WalletFile wallet : this.walletFile) {
      String address = wallet.getAddress();
      if (name.contains(address)) {
        return wallet;
      }
    }

    WalletFile wallet = WalletUtils.loadWalletFile(file);
    this.walletFile.add(wallet);
    return wallet;
  }

  public static boolean changeKeystorePassword(byte[] oldPassword, byte[] newPassowrd)
    throws IOException, CipherException {
    File wallet = selcetWalletFile();
    if (wallet == null) {
      throw new IOException(
        "No keystore file found, please use registerwallet or importwallet first!");
    }
    Credentials credentials = WalletUtils.loadCredentials(oldPassword, wallet);
    WalletUtils.updateWalletFile(newPassowrd, credentials.getEcKeyPair(), wallet, true);
    return true;
  }


  private static WalletFile loadWalletFile() throws IOException {
    File wallet = selcetWalletFile();
    if (wallet == null) {
      throw new IOException(
        "No keystore file found, please use registerwallet or importwallet first!");
    }
    return WalletUtils.loadWalletFile(wallet);
  }

  /**
   * load a Wallet from keystore
   */
  public static WalletApi loadWalletFromKeystore() throws IOException {
    WalletFile walletFile = loadWalletFile();
    WalletApi walletApi = new WalletApi(walletFile);
    return walletApi;
  }

  public byte[] getTrc10Address(String trc10) throws EncodingException {
    byte[] input = org.bouncycastle.util.encoders.Hex.decode(
      AbiUtil.parseMethod("mainToSideTRC10Map(uint256)", trc10, false));
    Contract.TriggerSmartContract triggerContract = triggerCallContract(getAddress(),
      sideGatewayAddress, 0, input, 0, "0");
    TransactionExtention transactionExtention = rpcSide.triggerContract(triggerContract);
    byte[] trc10Address = transactionExtention.getConstantResult(0).toByteArray();
    return MUtil.convertToTronAddress(new DataWord(trc10Address).getLast20Bytes());
  }

  public byte[] getSideTokenAddress(String mainAddress) throws EncodingException {
    byte[] input = org.bouncycastle.util.encoders.Hex.decode(
      AbiUtil.parseMethod("mainToSideContractMap(address)", "\"" + mainAddress + "\"", false));
    Contract.TriggerSmartContract triggerContract = triggerCallContract(getAddress(),
      sideGatewayAddress, 0, input, 0, "0");
    TransactionExtention transactionExtention = rpcSide.triggerContract(triggerContract);
    byte[] sideAddress = transactionExtention.getConstantResult(0).toByteArray();
    return MUtil.convertToTronAddress(new DataWord(sideAddress).getLast20Bytes());
  }

  public byte[] sideSignTrxData(String address, long trxNum)
    throws CipherException, IOException, EncodingException {
//    if (!WalletApi.addressValid(decodeFromBase58Check(address))) {
//          throw new RuntimeException("failed to signature, invalid address.");
//    }

    byte[] input = org.bouncycastle.util.encoders.Hex.decode(
      AbiUtil.parseMethod("nonces(address)", "\"" + encode58Check(getAddress()) + "\"", false));
    Contract.TriggerSmartContract triggerContract = triggerCallContract(getAddress(),
      mainGatewayAddress,
      0, input, 0, "0");
    TransactionExtention transactionExtention = rpcMain.triggerContract(triggerContract);
    byte[] nonceTemp = transactionExtention.getConstantResult(0).toByteArray();
    DataWord nonce = new DataWord(nonceTemp);

    byte[] amountTemp = ByteArray.fromLong(trxNum);
    DataWord amount = new DataWord(amountTemp);

    System.out.println("Please choose your key for sign.");
    WalletFile walletFile = selcetWalletFileE();
    System.out.println("Please input your password.");

    char[] password = Utils.inputPassword(false);
    byte[] passwd = org.tron.keystore.StringUtils.char2Byte(password);
    org.tron.keystore.StringUtils.clear(password);

    byte[] data = ByteUtil
      .merge(Arrays.copyOfRange(mainGatewayAddress, 1, mainGatewayAddress.length), nonce.getData(),
        amount.getData());
    ECKey myKey = getEcKey(walletFile, passwd);
    ECKey.ECDSASignature signature = myKey.sign(Hash.sha3(data));

    return signature.toByteArray();
  }

  public byte[] sideSignTokenData(String tokenAddress, String value)
    throws CipherException, IOException, EncodingException {
//    if (!WalletApi.addressValid(decodeFromBase58Check(address))) {
//          throw new RuntimeException("failed to signature, invalid address.");
//    }

    byte[] input = org.bouncycastle.util.encoders.Hex.decode(
      AbiUtil.parseMethod("nonces(address)", "\"" + encode58Check(getAddress()) + "\"", false));
    Contract.TriggerSmartContract triggerContract = triggerCallContract(getAddress(),
      mainGatewayAddress,
      0, input, 0, "0");
    TransactionExtention transactionExtention = rpcMain.triggerContract(triggerContract);
    byte[] nonceTemp = transactionExtention.getConstantResult(0).toByteArray();
    DataWord nonce = new DataWord(nonceTemp);

    DataWord valueI = new DataWord(new BigInteger(value, 10).toByteArray());

    System.out.println("Please choose your key for sign.");
    WalletFile walletFile = selcetWalletFileE();
    System.out.println("Please input your password.");

    char[] password = Utils.inputPassword(false);
    byte[] passwd = org.tron.keystore.StringUtils.char2Byte(password);
    org.tron.keystore.StringUtils.clear(password);

    byte[] mainTokenAddress = WalletApi.decode58Check(tokenAddress);
    if (mainTokenAddress == null || mainTokenAddress.length == 0) {
      System.out.println("Invalide adreess: " + tokenAddress);
      return null;
    }
    byte[] data = ByteUtil
      .merge(Arrays.copyOfRange(mainTokenAddress, 1, mainTokenAddress.length), nonce.getData(),
        valueI.getData());
    ECKey myKey = getEcKey(walletFile, passwd);
    ECKey.ECDSASignature signature = myKey.sign(Hash.sha3(data));

    return signature.toByteArray();
  }

  public byte[] sideSignTrc10Data(String tc10, String value)
    throws CipherException, IOException, EncodingException {
//    if (!WalletApi.addressValid(decodeFromBase58Check(address))) {
//          throw new RuntimeException("failed to signature, invalid address.");
//    }

    byte[] input = org.bouncycastle.util.encoders.Hex.decode(
      AbiUtil.parseMethod("nonces(address)", "\"" + encode58Check(getAddress()) + "\"", false));
    Contract.TriggerSmartContract triggerContract = triggerCallContract(getAddress(),
      mainGatewayAddress,
      0, input, 0, "0");
    TransactionExtention transactionExtention = rpcMain.triggerContract(triggerContract);
    byte[] nonceTemp = transactionExtention.getConstantResult(0).toByteArray();
    DataWord nonce = new DataWord(nonceTemp);

    DataWord valueI = new DataWord(new BigInteger(value, 10).toByteArray());
    DataWord tc10I = new DataWord(new BigInteger(tc10, 10).toByteArray());

    System.out.println("Please choose your key for sign.");
    WalletFile walletFile = selcetWalletFileE();
    System.out.println("Please input your password.");

    char[] password = Utils.inputPassword(false);
    byte[] passwd = org.tron.keystore.StringUtils.char2Byte(password);
    org.tron.keystore.StringUtils.clear(password);

    byte[] data = ByteUtil
      .merge(tc10I.getData(), nonce.getData(), valueI.getData());
    ECKey myKey = getEcKey(walletFile, passwd);
    ECKey.ECDSASignature signature = myKey.sign(Hash.sha3(data));

    return signature.toByteArray();
  }

  public  void sideGetMappingAddress(String sideGateway, String mainContractAddress)
          throws EncodingException {
    byte[] input = org.bouncycastle.util.encoders.Hex.decode(
            AbiUtil.parseMethod("mainToSideContractMap(address)", "\"" + mainContractAddress + "\"", false));

    Contract.TriggerSmartContract triggerContract = triggerCallContract(getAddress(),
            decode58Check(sideGateway),
            0, input, 0, "0");
    TransactionExtention transactionExtention = rpcCli.triggerContract(triggerContract);
    byte[] data = transactionExtention.getConstantResult(0).toByteArray();
    byte[] address = Arrays.copyOfRange(data, 12, data.length);

    System.out.println("sideContractAddress is " + encode58Check(ByteArray.convertToTronAddress(address)));
  }

  public Account queryAccount() {
    return queryAccount(getAddress());
  }

  public static Account queryAccount(byte[] address) {
    return rpcCli.queryAccount(address);//call rpc
  }

  public static Account queryAccountById(String accountId) {
    return rpcCli.queryAccountById(accountId);
  }

  private boolean confirm() {
    Scanner in = new Scanner(System.in);
    while (true) {
      String input = in.nextLine().trim();
      String str = input.split("\\s+")[0];
      if ("y".equalsIgnoreCase(str)) {
        return true;
      } else {
        return false;
      }
    }
  }
  private byte[] getCurrentChainId() {
    if(rpcCli == rpcMain) {
      return new byte[0];
    }

    return ByteArray.fromBytes21List(mainChainGateWayList);
  }
  private Transaction signTransaction(Transaction transaction)
    throws CipherException, IOException, CancelException {
    if (transaction.getRawData().getTimestamp() == 0) {
      transaction = TransactionUtils.setTimestamp(transaction);
    }
    transaction = TransactionUtils.setExpirationTime(transaction);
    System.out.println("Your transaction details are as follows, please confirm.");
    System.out
      .println("transaction hex string is " + ByteArray.toHexString(transaction.toByteArray()));
    System.out.println(Utils.printTransaction(transaction));

    System.out.println(
      "Please confirm and input your permission id, if input y or Y means default 0, other non-numeric characters will cancell transaction.");
    transaction = TransactionUtils.setPermissionId(transaction);
    while (true) {
      System.out.println("Please choose your key for sign.");
      WalletFile walletFile = selcetWalletFileE();
      System.out.println("Please input your password.");
      char[] password = Utils.inputPassword(false);
      byte[] passwd = org.tron.keystore.StringUtils.char2Byte(password);
      org.tron.keystore.StringUtils.clear(password);

      transaction = TransactionUtils.sign(transaction, this.getEcKey(walletFile, passwd), getCurrentChainId(), isMainChain());
      System.out
        .println("current transaction hex string is " + ByteArray
          .toHexString(transaction.toByteArray()));

      System.out
        .println("transaction hash is " + ByteArray
          .toHexString(Sha256Hash.hash(transaction.getRawData().toByteArray())));

      org.tron.keystore.StringUtils.clear(passwd);

      TransactionSignWeight weight = getTransactionSignWeight(transaction);
      if (weight.getResult().getCode() == response_code.ENOUGH_PERMISSION) {
        break;
      }
      if (weight.getResult().getCode() == response_code.NOT_ENOUGH_PERMISSION) {
        System.out.println("Current signWeight is:");
        System.out.println(Utils.printTransactionSignWeight(weight));
        System.out.println("Please confirm if continue add signature enter y or Y, else any other");
        if (!confirm()) {
          throw new CancelException("User cancelled");
        }
        continue;
      }
      throw new CancelException(weight.getResult().getMessage());
    }
    return transaction;
  }


  private boolean processTransactionExtention(TransactionExtention transactionExtention)
    throws IOException, CipherException, CancelException {
    return StringUtils.isNoneEmpty(processTransactionExt(transactionExtention));
  }

  private String processTransactionExt(TransactionExtention transactionExtention) throws CipherException, IOException, CancelException {
    if (transactionExtention == null) {
      return null;
    }
    Return ret = transactionExtention.getResult();
    if (!ret.getResult()) {
      System.out.println("Code = " + ret.getCode());
      System.out.println("Message = " + ret.getMessage().toStringUtf8());
      return null;
    }
    Transaction transaction = transactionExtention.getTransaction();
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      System.out.println("Transaction is empty");
      return null;
    }
    System.out.println(
      "Receive txid = " + ByteArray.toHexString(transactionExtention.getTxid().toByteArray()));
    System.out.println("transaction hex string is " + Utils.printTransaction(transaction));
    System.out.println(Utils.printTransaction(transactionExtention));
    transaction = signTransaction(transaction);

    ByteString txid = ByteString.copyFrom(Sha256Hash.hash(transaction.getRawData().toByteArray()));
    transactionExtention=transactionExtention.toBuilder().setTransaction(transaction).setTxid(txid).build();
    if(rpcCli.broadcastTransaction(transaction)){
      return ByteArray.toHexString(transactionExtention.getTxid().toByteArray());
    }

    return null;
  }

  private boolean processTransaction(Transaction transaction)
    throws IOException, CipherException, CancelException {
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }
    transaction = signTransaction(transaction);
    return rpcCli.broadcastTransaction(transaction);
  }

  //Warning: do not invoke this interface provided by others.
  public static Transaction signTransactionByApi(Transaction transaction, byte[] privateKey)
    throws CancelException {
    transaction = TransactionUtils.setExpirationTime(transaction);
    System.out.println("Please input permission id.");
    transaction = TransactionUtils.setPermissionId(transaction);
    TransactionSign.Builder builder = TransactionSign.newBuilder();
    builder.setPrivateKey(ByteString.copyFrom(privateKey));
    builder.setTransaction(transaction);
    return rpcCli.signTransaction(builder.build());
  }

  //Warning: do not invoke this interface provided by others.
  public static TransactionExtention signTransactionByApi2(Transaction transaction,
    byte[] privateKey) throws CancelException {
    transaction = TransactionUtils.setExpirationTime(transaction);
    System.out.println("Please input permission id.");
    transaction = TransactionUtils.setPermissionId(transaction);
    TransactionSign.Builder builder = TransactionSign.newBuilder();
    builder.setPrivateKey(ByteString.copyFrom(privateKey));
    builder.setTransaction(transaction);
    return rpcCli.signTransaction2(builder.build());
  }

  //Warning: do not invoke this interface provided by others.
  public static TransactionExtention addSignByApi(Transaction transaction,
    byte[] privateKey) throws CancelException {
    transaction = TransactionUtils.setExpirationTime(transaction);
    System.out.println("Please input permission id.");
    transaction = TransactionUtils.setPermissionId(transaction);
    TransactionSign.Builder builder = TransactionSign.newBuilder();
    builder.setPrivateKey(ByteString.copyFrom(privateKey));
    builder.setTransaction(transaction);
    return rpcCli.addSign(builder.build());
  }

  public static TransactionSignWeight getTransactionSignWeight(Transaction transaction) {
    return rpcCli.getTransactionSignWeight(transaction);
  }

  public static TransactionApprovedList getTransactionApprovedList(Transaction transaction) {
    return rpcCli.getTransactionApprovedList(transaction);
  }

  //Warning: do not invoke this interface provided by others.
  public static byte[] createAdresss(byte[] passPhrase) {
    return rpcCli.createAdresss(passPhrase);
  }

  //Warning: do not invoke this interface provided by others.
  public static EasyTransferResponse easyTransfer(byte[] passPhrase, byte[] toAddress,
    long amount) {
    return rpcCli.easyTransfer(passPhrase, toAddress, amount);
  }

  //Warning: do not invoke this interface provided by others.
  public static EasyTransferResponse easyTransferByPrivate(byte[] privateKey, byte[] toAddress,
    long amount) {
    return rpcCli.easyTransferByPrivate(privateKey, toAddress, amount);
  }

  //Warning: do not invoke this interface provided by others.
  public static EasyTransferResponse easyTransferAsset(byte[] passPhrase, byte[] toAddress,
    String assetId, long amount) {
    return rpcCli.easyTransferAsset(passPhrase, toAddress, assetId, amount);
  }

  //Warning: do not invoke this interface provided by others.
  public static EasyTransferResponse easyTransferAssetByPrivate(byte[] privateKey,
    byte[] toAddress, String assetId, long amount) {
    return rpcCli.easyTransferAssetByPrivate(privateKey, toAddress, assetId, amount);
  }

  public boolean sendCoin(byte[] to, long amount)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.TransferContract contract = createTransferContract(to, owner, amount);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createTransaction(contract);
      return processTransaction(transaction);
    }
  }

  public boolean updateAccount(byte[] accountNameBytes)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.AccountUpdateContract contract = createAccountUpdateContract(accountNameBytes, owner);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createTransaction(contract);
      return processTransaction(transaction);
    }
  }

  public boolean setAccountId(byte[] accountIdBytes)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.SetAccountIdContract contract = createSetAccountIdContract(accountIdBytes, owner);
    Transaction transaction = rpcCli.createTransaction(contract);

    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }

    transaction = signTransaction(transaction);
    return rpcCli.broadcastTransaction(transaction);
  }


  public boolean updateAsset(byte[] description, byte[] url, long newLimit,
    long newPublicLimit)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.UpdateAssetContract contract
      = createUpdateAssetContract(owner, description, url, newLimit, newPublicLimit);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createTransaction(contract);
      return processTransaction(transaction);
    }
  }

  public boolean transferAsset(byte[] to, byte[] assertName, long amount)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.TransferAssetContract contract = createTransferAssetContract(to, assertName, owner,
      amount);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createTransferAssetTransaction2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createTransferAssetTransaction(contract);
      return processTransaction(transaction);
    }
  }

  public boolean participateAssetIssue(byte[] to, byte[] assertName, long amount)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.ParticipateAssetIssueContract contract = participateAssetIssueContract(to, assertName,
      owner, amount);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli
        .createParticipateAssetIssueTransaction2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createParticipateAssetIssueTransaction(contract);
      return processTransaction(transaction);
    }
  }

  public static boolean broadcastTransaction(byte[] transactionBytes)
    throws InvalidProtocolBufferException {
    Transaction transaction = Transaction.parseFrom(transactionBytes);
    return rpcCli.broadcastTransaction(transaction);
  }

  public static boolean broadcastTransaction(Transaction transaction) {
    return rpcCli.broadcastTransaction(transaction);
  }

  public boolean createAssetIssue(Contract.AssetIssueContract contract)
    throws CipherException, IOException, CancelException {
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createAssetIssue2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createAssetIssue(contract);
      return processTransaction(transaction);
    }
  }

  public boolean createAccount(byte[] address)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.AccountCreateContract contract = createAccountCreateContract(owner, address);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createAccount2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createAccount(contract);
      return processTransaction(transaction);
    }
  }

  //Warning: do not invoke this interface provided by others.
  public static AddressPrKeyPairMessage generateAddress() {
    EmptyMessage.Builder builder = EmptyMessage.newBuilder();
    return rpcCli.generateAddress(builder.build());
  }

  public boolean createWitness(byte[] url) throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.WitnessCreateContract contract = createWitnessCreateContract(owner, url);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createWitness2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createWitness(contract);
      return processTransaction(transaction);
    }
  }

  public boolean updateWitness(byte[] url) throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.WitnessUpdateContract contract = createWitnessUpdateContract(owner, url);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.updateWitness2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.updateWitness(contract);
      return processTransaction(transaction);
    }
  }

  public static Block getBlock(long blockNum) {
    return rpcCli.getBlock(blockNum);
  }

  public static BlockExtention getBlock2(long blockNum) {
    return rpcCli.getBlock2(blockNum);
  }

  public static long getTransactionCountByBlockNum(long blockNum) {
    return rpcCli.getTransactionCountByBlockNum(blockNum);
  }

  public boolean voteWitness(HashMap<String, String> witness)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.VoteWitnessContract contract = createVoteWitnessContract(owner, witness);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.voteWitnessAccount2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.voteWitnessAccount(contract);
      return processTransaction(transaction);
    }
  }

  public static Contract.TransferContract createTransferContract(byte[] to, byte[] owner,
    long amount) {
    Contract.TransferContract.Builder builder = Contract.TransferContract.newBuilder();
    ByteString bsTo = ByteString.copyFrom(to);
    ByteString bsOwner = ByteString.copyFrom(owner);
    builder.setToAddress(bsTo);
    builder.setOwnerAddress(bsOwner);
    builder.setAmount(amount);

    return builder.build();
  }

  public static Contract.TransferAssetContract createTransferAssetContract(byte[] to,
    byte[] assertName, byte[] owner,
    long amount) {
    Contract.TransferAssetContract.Builder builder = Contract.TransferAssetContract.newBuilder();
    ByteString bsTo = ByteString.copyFrom(to);
    ByteString bsName = ByteString.copyFrom(assertName);
    ByteString bsOwner = ByteString.copyFrom(owner);
    builder.setToAddress(bsTo);
    builder.setAssetName(bsName);
    builder.setOwnerAddress(bsOwner);
    builder.setAmount(amount);

    return builder.build();
  }

  public static Contract.ParticipateAssetIssueContract participateAssetIssueContract(byte[] to,
    byte[] assertName, byte[] owner,
    long amount) {
    Contract.ParticipateAssetIssueContract.Builder builder = Contract.ParticipateAssetIssueContract
      .newBuilder();
    ByteString bsTo = ByteString.copyFrom(to);
    ByteString bsName = ByteString.copyFrom(assertName);
    ByteString bsOwner = ByteString.copyFrom(owner);
    builder.setToAddress(bsTo);
    builder.setAssetName(bsName);
    builder.setOwnerAddress(bsOwner);
    builder.setAmount(amount);

    return builder.build();
  }

  public static Contract.AccountUpdateContract createAccountUpdateContract(byte[] accountName,
    byte[] address) {
    Contract.AccountUpdateContract.Builder builder = Contract.AccountUpdateContract.newBuilder();
    ByteString basAddreess = ByteString.copyFrom(address);
    ByteString bsAccountName = ByteString.copyFrom(accountName);
    builder.setAccountName(bsAccountName);
    builder.setOwnerAddress(basAddreess);

    return builder.build();
  }

  public static Contract.SetAccountIdContract createSetAccountIdContract(byte[] accountId,
    byte[] address) {
    Contract.SetAccountIdContract.Builder builder = Contract.SetAccountIdContract.newBuilder();
    ByteString bsAddress = ByteString.copyFrom(address);
    ByteString bsAccountId = ByteString.copyFrom(accountId);
    builder.setAccountId(bsAccountId);
    builder.setOwnerAddress(bsAddress);

    return builder.build();
  }


  public static Contract.UpdateAssetContract createUpdateAssetContract(
    byte[] address,
    byte[] description,
    byte[] url,
    long newLimit,
    long newPublicLimit
  ) {
    Contract.UpdateAssetContract.Builder builder =
      Contract.UpdateAssetContract.newBuilder();
    ByteString basAddreess = ByteString.copyFrom(address);
    builder.setDescription(ByteString.copyFrom(description));
    builder.setUrl(ByteString.copyFrom(url));
    builder.setNewLimit(newLimit);
    builder.setNewPublicLimit(newPublicLimit);
    builder.setOwnerAddress(basAddreess);

    return builder.build();
  }

  public static Contract.AccountCreateContract createAccountCreateContract(byte[] owner,
    byte[] address) {
    Contract.AccountCreateContract.Builder builder = Contract.AccountCreateContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    builder.setAccountAddress(ByteString.copyFrom(address));

    return builder.build();
  }

  public static Contract.WitnessCreateContract createWitnessCreateContract(byte[] owner,
    byte[] url) {
    Contract.WitnessCreateContract.Builder builder = Contract.WitnessCreateContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    builder.setUrl(ByteString.copyFrom(url));

    return builder.build();
  }

  public static Contract.WitnessUpdateContract createWitnessUpdateContract(byte[] owner,
    byte[] url) {
    Contract.WitnessUpdateContract.Builder builder = Contract.WitnessUpdateContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    builder.setUpdateUrl(ByteString.copyFrom(url));

    return builder.build();
  }

  public static Contract.VoteWitnessContract createVoteWitnessContract(byte[] owner,
    HashMap<String, String> witness) {
    Contract.VoteWitnessContract.Builder builder = Contract.VoteWitnessContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    for (String addressBase58 : witness.keySet()) {
      String value = witness.get(addressBase58);
      long count = Long.parseLong(value);
      Contract.VoteWitnessContract.Vote.Builder voteBuilder = Contract.VoteWitnessContract.Vote
        .newBuilder();
      byte[] address = WalletApi.decodeFromBase58Check(addressBase58);
      if (address == null) {
        continue;
      }
      voteBuilder.setVoteAddress(ByteString.copyFrom(address));
      voteBuilder.setVoteCount(count);
      builder.addVotes(voteBuilder.build());
    }

    return builder.build();
  }

  public static boolean passwordValid(char[] password) {
    if (ArrayUtils.isEmpty(password)) {
      throw new IllegalArgumentException("password is empty");
    }
    if (password.length < 6) {
      logger.warn("Warning: Password is too short !!");
      return false;
    }
    //Other rule;
    int level = CheckStrength.checkPasswordStrength(password);
    if (level <= 4) {
      System.out.println("Your password is too weak!");
      System.out.println("The password should be at least 8 characters.");
      System.out.println("The password should contains uppercase, lowercase, numeric and other.");
      System.out.println(
        "The password should not contain more than 3 duplicate numbers or letters; For example: 1111.");
      System.out.println(
        "The password should not contain more than 3 consecutive Numbers or letters; For example: 1234.");
      System.out.println("The password should not contain weak password combination; For example:");
      System.out.println("ababab, abcabc, password, passw0rd, p@ssw0rd, admin1234, etc.");
      return false;
    }
    return true;
  }

  public static boolean addressValid(byte[] address) {
    if (ArrayUtils.isEmpty(address)) {
      logger.warn("Warning: Address is empty !!");
      return false;
    }
    if (address.length != CommonConstant.ADDRESS_SIZE) {
      logger.warn(
        "Warning: Address length need " + CommonConstant.ADDRESS_SIZE + " but " + address.length
          + " !!");
      return false;
    }
    byte preFixbyte = address[0];
    if (preFixbyte != WalletApi.getAddressPreFixByte()) {
      logger
        .warn("Warning: Address need prefix with " + WalletApi.getAddressPreFixByte() + " but "
          + preFixbyte + " !!");
      return false;
    }
    //Other rule;
    return true;
  }

  public static String encode58Check(byte[] input) {
    byte[] hash0 = Sha256Hash.hash(input);
    byte[] hash1 = Sha256Hash.hash(hash0);
    byte[] inputCheck = new byte[input.length + 4];
    System.arraycopy(input, 0, inputCheck, 0, input.length);
    System.arraycopy(hash1, 0, inputCheck, input.length, 4);
    return Base58.encode(inputCheck);
  }

  private static byte[] decode58Check(String input) {
    byte[] decodeCheck = Base58.decode(input);
    if (decodeCheck.length <= 4) {
      return null;
    }
    byte[] decodeData = new byte[decodeCheck.length - 4];
    System.arraycopy(decodeCheck, 0, decodeData, 0, decodeData.length);
    byte[] hash0 = Sha256Hash.hash(decodeData);
    byte[] hash1 = Sha256Hash.hash(hash0);
    if (hash1[0] == decodeCheck[decodeData.length] &&
      hash1[1] == decodeCheck[decodeData.length + 1] &&
      hash1[2] == decodeCheck[decodeData.length + 2] &&
      hash1[3] == decodeCheck[decodeData.length + 3]) {
      return decodeData;
    }
    return null;
  }

  public static byte[] decodeFromBase58Check(String addressBase58) {
    if (StringUtils.isEmpty(addressBase58)) {
      logger.warn("Warning: Address is empty !!");
      return null;
    }
    byte[] address = decode58Check(addressBase58);
    if (!addressValid(address)) {
      return null;
    }
    return address;
  }

  public static boolean priKeyValid(byte[] priKey) {
    if (ArrayUtils.isEmpty(priKey)) {
      logger.warn("Warning: PrivateKey is empty !!");
      return false;
    }
    if (priKey.length != 32) {
      logger.warn("Warning: PrivateKey length need 64 but " + priKey.length + " !!");
      return false;
    }
    //Other rule;
    return true;
  }

//  public static Optional<AccountList> listAccounts() {
//    Optional<AccountList> result = rpcCli.listAccounts();
//    if (result.isPresent()) {
//      AccountList accountList = result.get();
//      List<Account> list = accountList.getAccountsList();
//      List<Account> newList = new ArrayList();
//      newList.addAll(list);
//      newList.sort(new AccountComparator());
//      AccountList.Builder builder = AccountList.newBuilder();
//      newList.forEach(account -> builder.addAccounts(account));
//      result = Optional.of(builder.build());
//    }
//    return result;
//  }

  public static Optional<WitnessList> listWitnesses() {
    Optional<WitnessList> result = rpcCli.listWitnesses();
    if (result.isPresent()) {
      WitnessList witnessList = result.get();
      List<Witness> list = witnessList.getWitnessesList();
      List<Witness> newList = new ArrayList<>();
      newList.addAll(list);
      newList.sort(new Comparator<Witness>() {
        @Override
        public int compare(Witness o1, Witness o2) {
          return Long.compare(o2.getVoteCount(), o1.getVoteCount());
        }
      });
      WitnessList.Builder builder = WitnessList.newBuilder();
      newList.forEach(witness -> builder.addWitnesses(witness));
      result = Optional.of(builder.build());
    }
    return result;
  }

//  public static Optional<AssetIssueList> getAssetIssueListByTimestamp(long timestamp) {
//    return rpcCli.getAssetIssueListByTimestamp(timestamp);
//  }
//
//  public static Optional<TransactionList> getTransactionsByTimestamp(long start, long end,
//      int offset, int limit) {
//    return rpcCli.getTransactionsByTimestamp(start, end, offset, limit);
//  }
//
//  public static GrpcAPI.NumberMessage getTransactionsByTimestampCount(long start, long end) {
//    return rpcCli.getTransactionsByTimestampCount(start, end);
//  }

  public static Optional<AssetIssueList> getAssetIssueList() {
    return rpcCli.getAssetIssueList();
  }

  public static Optional<AssetIssueList> getAssetIssueList(long offset, long limit) {
    return rpcCli.getAssetIssueList(offset, limit);
  }

  public static Optional<ProposalList> getProposalListPaginated(long offset, long limit) {
    return rpcCli.getProposalListPaginated(offset, limit);
  }

  public static Optional<ExchangeList> getExchangeListPaginated(long offset, long limit) {
    return rpcCli.getExchangeListPaginated(offset, limit);
  }


  public static Optional<NodeList> listNodes() {
    return rpcCli.listNodes();
  }

  public static Optional<AssetIssueList> getAssetIssueByAccount(byte[] address) {
    return rpcCli.getAssetIssueByAccount(address);
  }

  public static AccountNetMessage getAccountNet(byte[] address) {
    return rpcCli.getAccountNet(address);
  }

  public static AccountResourceMessage getAccountResource(byte[] address) {
    return rpcCli.getAccountResource(address);
  }

  public static AssetIssueContract getAssetIssueByName(String assetName) {
    return rpcCli.getAssetIssueByName(assetName);
  }

  public static Optional<AssetIssueList> getAssetIssueListByName(String assetName) {
    return rpcCli.getAssetIssueListByName(assetName);
  }

  public static AssetIssueContract getAssetIssueById(String assetId) {
    return rpcCli.getAssetIssueById(assetId);
  }

  public static GrpcAPI.NumberMessage getTotalTransaction() {
    return rpcCli.getTotalTransaction();
  }

  public static GrpcAPI.NumberMessage getNextMaintenanceTime() {
    return rpcCli.getNextMaintenanceTime();
  }

  public static Optional<TransactionList> getTransactionsFromThis(byte[] address, int offset,
    int limit) {
    return rpcCli.getTransactionsFromThis(address, offset, limit);
  }

  public static Optional<TransactionListExtention> getTransactionsFromThis2(byte[] address,
    int offset,
    int limit) {
    return rpcCli.getTransactionsFromThis2(address, offset, limit);
  }
//  public static GrpcAPI.NumberMessage getTransactionsFromThisCount(byte[] address) {
//    return rpcCli.getTransactionsFromThisCount(address);
//  }

  public static Optional<TransactionList> getTransactionsToThis(byte[] address, int offset,
    int limit) {
    return rpcCli.getTransactionsToThis(address, offset, limit);
  }

  public static Optional<TransactionListExtention> getTransactionsToThis2(byte[] address,
    int offset,
    int limit) {
    return rpcCli.getTransactionsToThis2(address, offset, limit);
  }
//  public static GrpcAPI.NumberMessage getTransactionsToThisCount(byte[] address) {
//    return rpcCli.getTransactionsToThisCount(address);
//  }

  public static Optional<Transaction> getTransactionById(String txID) {
    return rpcCli.getTransactionById(txID);
  }

  public static Optional<TransactionInfo> getTransactionInfoById(String txID) {
    return rpcCli.getTransactionInfoById(txID);
  }

  public boolean freezeBalance(long frozen_balance, long frozen_duration, int resourceCode,
    String receiverAddress)
    throws CipherException, IOException, CancelException {
    Contract.FreezeBalanceContract contract = createFreezeBalanceContract(frozen_balance,
      frozen_duration, resourceCode, receiverAddress);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createTransaction(contract);
      return processTransaction(transaction);
    }
  }

  public boolean buyStorage(long quantity)
    throws CipherException, IOException, CancelException {
    Contract.BuyStorageContract contract = createBuyStorageContract(quantity);
    TransactionExtention transactionExtention = rpcCli.createTransaction(contract);
    return processTransactionExtention(transactionExtention);
  }

  public boolean buyStorageBytes(long bytes)
    throws CipherException, IOException, CancelException {
    Contract.BuyStorageBytesContract contract = createBuyStorageBytesContract(bytes);
    TransactionExtention transactionExtention = rpcCli.createTransaction(contract);
    return processTransactionExtention(transactionExtention);
  }

  public boolean sellStorage(long storageBytes)
    throws CipherException, IOException, CancelException {
    Contract.SellStorageContract contract = createSellStorageContract(storageBytes);
    TransactionExtention transactionExtention = rpcCli.createTransaction(contract);
    return processTransactionExtention(transactionExtention);

  }

  private FreezeBalanceContract createFreezeBalanceContract(long frozen_balance,
    long frozen_duration, int resourceCode, String receiverAddress) {
    byte[] address = getAddress();
    Contract.FreezeBalanceContract.Builder builder = Contract.FreezeBalanceContract.newBuilder();
    ByteString byteAddress = ByteString.copyFrom(address);
    builder.setOwnerAddress(byteAddress).setFrozenBalance(frozen_balance)
      .setFrozenDuration(frozen_duration).setResourceValue(resourceCode);

    if (receiverAddress != null && !receiverAddress.equals("")) {
      ByteString receiverAddressBytes = ByteString.copyFrom(
        Objects.requireNonNull(WalletApi.decodeFromBase58Check(receiverAddress)));
      builder.setReceiverAddress(receiverAddressBytes);
    }
    return builder.build();
  }

  private BuyStorageContract createBuyStorageContract(long quantity) {
    byte[] address = getAddress();
    Contract.BuyStorageContract.Builder builder = Contract.BuyStorageContract.newBuilder();
    ByteString byteAddress = ByteString.copyFrom(address);
    builder.setOwnerAddress(byteAddress).setQuant(quantity);

    return builder.build();
  }

  private BuyStorageBytesContract createBuyStorageBytesContract(long bytes) {
    byte[] address = getAddress();
    Contract.BuyStorageBytesContract.Builder builder = Contract.BuyStorageBytesContract
      .newBuilder();
    ByteString byteAddress = ByteString.copyFrom(address);
    builder.setOwnerAddress(byteAddress).setBytes(bytes);

    return builder.build();
  }

  private SellStorageContract createSellStorageContract(long storageBytes) {
    byte[] address = getAddress();
    Contract.SellStorageContract.Builder builder = Contract.SellStorageContract.newBuilder();
    ByteString byteAddress = ByteString.copyFrom(address);
    builder.setOwnerAddress(byteAddress).setStorageBytes(storageBytes);

    return builder.build();
  }

  public boolean unfreezeBalance(int resourceCode, String receiverAddress)
    throws CipherException, IOException, CancelException {
    Contract.UnfreezeBalanceContract contract = createUnfreezeBalanceContract(resourceCode,
      receiverAddress);
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createTransaction(contract);
      return processTransaction(transaction);
    }
  }


  private UnfreezeBalanceContract createUnfreezeBalanceContract(int resourceCode,
    String receiverAddress) {
    byte[] address = getAddress();
    Contract.UnfreezeBalanceContract.Builder builder = Contract.UnfreezeBalanceContract
      .newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);
    builder.setOwnerAddress(byteAddreess).setResourceValue(resourceCode);

    if (receiverAddress != null && !receiverAddress.equals("")) {
      ByteString receiverAddressBytes = ByteString.copyFrom(
        Objects.requireNonNull(WalletApi.decodeFromBase58Check(receiverAddress)));
      builder.setReceiverAddress(receiverAddressBytes);
    }

    return builder.build();
  }

  public boolean unfreezeAsset() throws CipherException, IOException, CancelException {
    Contract.UnfreezeAssetContract contract = createUnfreezeAssetContract();
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createTransaction(contract);
      return processTransaction(transaction);
    }
  }

  private UnfreezeAssetContract createUnfreezeAssetContract() {
    byte[] address = getAddress();
    Contract.UnfreezeAssetContract.Builder builder = Contract.UnfreezeAssetContract
      .newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);
    builder.setOwnerAddress(byteAddreess);
    return builder.build();
  }

  public boolean withdrawBalance() throws CipherException, IOException, CancelException {
    Contract.WithdrawBalanceContract contract = createWithdrawBalanceContract();
    if (rpcVersion == 2) {
      TransactionExtention transactionExtention = rpcCli.createTransaction2(contract);
      return processTransactionExtention(transactionExtention);
    } else {
      Transaction transaction = rpcCli.createTransaction(contract);
      return processTransaction(transaction);
    }
  }

  private WithdrawBalanceContract createWithdrawBalanceContract() {
    byte[] address = getAddress();
    Contract.WithdrawBalanceContract.Builder builder = Contract.WithdrawBalanceContract
      .newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);
    builder.setOwnerAddress(byteAddreess);

    return builder.build();
  }

  public static Optional<Block> getBlockById(String blockID) {
    return rpcCli.getBlockById(blockID);
  }

  public static Optional<BlockList> getBlockByLimitNext(long start, long end) {
    return rpcCli.getBlockByLimitNext(start, end);
  }

  public static Optional<BlockListExtention> getBlockByLimitNext2(long start, long end) {
    return rpcCli.getBlockByLimitNext2(start, end);
  }

  public static Optional<BlockList> getBlockByLatestNum(long num) {
    return rpcCli.getBlockByLatestNum(num);
  }

  public static Optional<BlockListExtention> getBlockByLatestNum2(long num) {
    return rpcCli.getBlockByLatestNum2(num);
  }

  public boolean createProposal(HashMap<Long, Long> parametersMap)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.ProposalCreateContract contract = createProposalCreateContract(owner, parametersMap);
    TransactionExtention transactionExtention = rpcCli.proposalCreate(contract);
    return processTransactionExtention(transactionExtention);
  }

  public static Contract.SideChainProposalCreateContract sideCreateProposalCreateContract(
    byte[] owner,
    HashMap<Long, String> parametersMap) {
    Contract.SideChainProposalCreateContract.Builder builder = Contract.SideChainProposalCreateContract
      .newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    builder.putAllParameters(parametersMap);
    return builder.build();
  }

  public boolean sideChainCreateProposal(HashMap<Long, String> parametersMap)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.SideChainProposalCreateContract contract = sideCreateProposalCreateContract(owner,
      parametersMap);
    TransactionExtention transactionExtention = rpcCli.SideProposalCreate(contract);
    return processTransactionExtention(transactionExtention);
  }

  public static Optional<ProposalList> listProposals() {
    return rpcCli.listProposals();
  }

  public static Optional<SideChainProposalList> sideChainListProposals() {
    return rpcCli.sideChianListProposals();
  }

  public static Optional<Proposal> getProposal(String id) {
    return rpcCli.getProposal(id);
  }

  public static Optional<DelegatedResourceList> getDelegatedResource(String fromAddress,
    String toAddress) {
    return rpcCli.getDelegatedResource(fromAddress, toAddress);
  }

  public static Optional<DelegatedResourceAccountIndex> getDelegatedResourceAccountIndex(
    String address) {
    return rpcCli.getDelegatedResourceAccountIndex(address);
  }

  public static Optional<ExchangeList> listExchanges() {
    return rpcCli.listExchanges();
  }

  public static Optional<Exchange> getExchange(String id) {
    return rpcCli.getExchange(id);
  }

  public static Optional<ChainParameters> getChainParameters() {
    return rpcCli.getChainParameters();
  }


  public static Contract.ProposalCreateContract createProposalCreateContract(byte[] owner,
    HashMap<Long, Long> parametersMap) {
    Contract.ProposalCreateContract.Builder builder = Contract.ProposalCreateContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    builder.putAllParameters(parametersMap);
    return builder.build();
  }

  public boolean approveProposal(long id, boolean is_add_approval)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.ProposalApproveContract contract = createProposalApproveContract(owner, id,
      is_add_approval);
    TransactionExtention transactionExtention = rpcCli.proposalApprove(contract);
    return processTransactionExtention(transactionExtention);
  }

  public static Contract.ProposalApproveContract createProposalApproveContract(byte[] owner,
    long id, boolean is_add_approval) {
    Contract.ProposalApproveContract.Builder builder = Contract.ProposalApproveContract
      .newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    builder.setProposalId(id);
    builder.setIsAddApproval(is_add_approval);
    return builder.build();
  }

  public boolean deleteProposal(long id)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.ProposalDeleteContract contract = createProposalDeleteContract(owner, id);
    TransactionExtention transactionExtention = rpcCli.proposalDelete(contract);
    return processTransactionExtention(transactionExtention);
  }

  public static Contract.ProposalDeleteContract createProposalDeleteContract(byte[] owner,
    long id) {
    Contract.ProposalDeleteContract.Builder builder = Contract.ProposalDeleteContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    builder.setProposalId(id);
    return builder.build();
  }

  public boolean exchangeCreate(byte[] firstTokenId, long firstTokenBalance,
    byte[] secondTokenId, long secondTokenBalance)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.ExchangeCreateContract contract = createExchangeCreateContract(owner, firstTokenId,
      firstTokenBalance, secondTokenId, secondTokenBalance);
    TransactionExtention transactionExtention = rpcCli.exchangeCreate(contract);
    return processTransactionExtention(transactionExtention);
  }

  public static Contract.ExchangeCreateContract createExchangeCreateContract(byte[] owner,
    byte[] firstTokenId, long firstTokenBalance,
    byte[] secondTokenId, long secondTokenBalance) {
    Contract.ExchangeCreateContract.Builder builder = Contract.ExchangeCreateContract.newBuilder();
    builder
      .setOwnerAddress(ByteString.copyFrom(owner))
      .setFirstTokenId(ByteString.copyFrom(firstTokenId))
      .setFirstTokenBalance(firstTokenBalance)
      .setSecondTokenId(ByteString.copyFrom(secondTokenId))
      .setSecondTokenBalance(secondTokenBalance);
    return builder.build();
  }

  public boolean exchangeInject(long exchangeId, byte[] tokenId, long quant)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.ExchangeInjectContract contract = createExchangeInjectContract(owner, exchangeId,
      tokenId, quant);
    TransactionExtention transactionExtention = rpcCli.exchangeInject(contract);
    return processTransactionExtention(transactionExtention);
  }

  public static Contract.ExchangeInjectContract createExchangeInjectContract(byte[] owner,
    long exchangeId, byte[] tokenId, long quant) {
    Contract.ExchangeInjectContract.Builder builder = Contract.ExchangeInjectContract.newBuilder();
    builder
      .setOwnerAddress(ByteString.copyFrom(owner))
      .setExchangeId(exchangeId)
      .setTokenId(ByteString.copyFrom(tokenId))
      .setQuant(quant);
    return builder.build();
  }

  public boolean exchangeWithdraw(long exchangeId, byte[] tokenId, long quant)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.ExchangeWithdrawContract contract = createExchangeWithdrawContract(owner, exchangeId,
      tokenId, quant);
    TransactionExtention transactionExtention = rpcCli.exchangeWithdraw(contract);
    return processTransactionExtention(transactionExtention);
  }

  public static Contract.ExchangeWithdrawContract createExchangeWithdrawContract(byte[] owner,
    long exchangeId, byte[] tokenId, long quant) {
    Contract.ExchangeWithdrawContract.Builder builder = Contract.ExchangeWithdrawContract
      .newBuilder();
    builder
      .setOwnerAddress(ByteString.copyFrom(owner))
      .setExchangeId(exchangeId)
      .setTokenId(ByteString.copyFrom(tokenId))
      .setQuant(quant);
    return builder.build();
  }

  public boolean exchangeTransaction(long exchangeId, byte[] tokenId, long quant, long expected)
    throws CipherException, IOException, CancelException {
    byte[] owner = getAddress();
    Contract.ExchangeTransactionContract contract = createExchangeTransactionContract(owner,
      exchangeId, tokenId, quant, expected);
    TransactionExtention transactionExtention = rpcCli.exchangeTransaction(contract);
    return processTransactionExtention(transactionExtention);
  }

  public static Contract.ExchangeTransactionContract createExchangeTransactionContract(byte[] owner,
    long exchangeId, byte[] tokenId, long quant, long expected) {
    Contract.ExchangeTransactionContract.Builder builder = Contract.ExchangeTransactionContract
      .newBuilder();
    builder
      .setOwnerAddress(ByteString.copyFrom(owner))
      .setExchangeId(exchangeId)
      .setTokenId(ByteString.copyFrom(tokenId))
      .setQuant(quant)
      .setExpected(expected);
    return builder.build();
  }


  public static SmartContract.ABI.Entry.EntryType getEntryType(String type) {
    switch (type) {
      case "constructor":
        return SmartContract.ABI.Entry.EntryType.Constructor;
      case "function":
        return SmartContract.ABI.Entry.EntryType.Function;
      case "event":
        return SmartContract.ABI.Entry.EntryType.Event;
      case "fallback":
        return SmartContract.ABI.Entry.EntryType.Fallback;
      default:
        return SmartContract.ABI.Entry.EntryType.UNRECOGNIZED;
    }
  }

  public static SmartContract.ABI.Entry.StateMutabilityType getStateMutability(
    String stateMutability) {
    switch (stateMutability) {
      case "pure":
        return SmartContract.ABI.Entry.StateMutabilityType.Pure;
      case "view":
        return SmartContract.ABI.Entry.StateMutabilityType.View;
      case "nonpayable":
        return SmartContract.ABI.Entry.StateMutabilityType.Nonpayable;
      case "payable":
        return SmartContract.ABI.Entry.StateMutabilityType.Payable;
      default:
        return SmartContract.ABI.Entry.StateMutabilityType.UNRECOGNIZED;
    }
  }

  public static SmartContract.ABI jsonStr2ABI(String jsonStr) {
    if (jsonStr == null) {
      return null;
    }

    JsonParser jsonParser = new JsonParser();
    JsonElement jsonElementRoot = jsonParser.parse(jsonStr);
    JsonArray jsonRoot = jsonElementRoot.getAsJsonArray();
    SmartContract.ABI.Builder abiBuilder = SmartContract.ABI.newBuilder();
    for (int index = 0; index < jsonRoot.size(); index++) {
      JsonElement abiItem = jsonRoot.get(index);
      boolean anonymous = abiItem.getAsJsonObject().get("anonymous") != null ?
        abiItem.getAsJsonObject().get("anonymous").getAsBoolean() : false;
      boolean constant = abiItem.getAsJsonObject().get("constant") != null ?
        abiItem.getAsJsonObject().get("constant").getAsBoolean() : false;
      String name = abiItem.getAsJsonObject().get("name") != null ?
        abiItem.getAsJsonObject().get("name").getAsString() : null;
      JsonArray inputs = abiItem.getAsJsonObject().get("inputs") != null ?
        abiItem.getAsJsonObject().get("inputs").getAsJsonArray() : null;
      JsonArray outputs = abiItem.getAsJsonObject().get("outputs") != null ?
        abiItem.getAsJsonObject().get("outputs").getAsJsonArray() : null;
      String type = abiItem.getAsJsonObject().get("type") != null ?
        abiItem.getAsJsonObject().get("type").getAsString() : null;
      boolean payable = abiItem.getAsJsonObject().get("payable") != null ?
        abiItem.getAsJsonObject().get("payable").getAsBoolean() : false;
      String stateMutability = abiItem.getAsJsonObject().get("stateMutability") != null ?
        abiItem.getAsJsonObject().get("stateMutability").getAsString() : null;
      if (type == null) {
        logger.error("No type!");
        return null;
      }
      if (!type.equalsIgnoreCase("fallback") && null == inputs) {
        logger.error("No inputs!");
        return null;
      }

      SmartContract.ABI.Entry.Builder entryBuilder = SmartContract.ABI.Entry.newBuilder();
      entryBuilder.setAnonymous(anonymous);
      entryBuilder.setConstant(constant);
      if (name != null) {
        entryBuilder.setName(name);
      }

      /* { inputs : optional } since fallback function not requires inputs*/
      if (null != inputs) {
        for (int j = 0; j < inputs.size(); j++) {
          JsonElement inputItem = inputs.get(j);
          if (inputItem.getAsJsonObject().get("name") == null ||
            inputItem.getAsJsonObject().get("type") == null) {
            logger.error("Input argument invalid due to no name or no type!");
            return null;
          }
          String inputName = inputItem.getAsJsonObject().get("name").getAsString();
          String inputType = inputItem.getAsJsonObject().get("type").getAsString();
          Boolean inputIndexed = false;
          if (inputItem.getAsJsonObject().get("indexed") != null) {
            inputIndexed = Boolean
              .valueOf(inputItem.getAsJsonObject().get("indexed").getAsString());
          }
          SmartContract.ABI.Entry.Param.Builder paramBuilder = SmartContract.ABI.Entry.Param
            .newBuilder();
          paramBuilder.setIndexed(inputIndexed);
          paramBuilder.setName(inputName);
          paramBuilder.setType(inputType);
          entryBuilder.addInputs(paramBuilder.build());
        }
      }

      /* { outputs : optional } */
      if (outputs != null) {
        for (int k = 0; k < outputs.size(); k++) {
          JsonElement outputItem = outputs.get(k);
          if (outputItem.getAsJsonObject().get("name") == null ||
            outputItem.getAsJsonObject().get("type") == null) {
            logger.error("Output argument invalid due to no name or no type!");
            return null;
          }
          String outputName = outputItem.getAsJsonObject().get("name").getAsString();
          String outputType = outputItem.getAsJsonObject().get("type").getAsString();
          Boolean outputIndexed = false;
          if (outputItem.getAsJsonObject().get("indexed") != null) {
            outputIndexed = Boolean
              .valueOf(outputItem.getAsJsonObject().get("indexed").getAsString());
          }
          SmartContract.ABI.Entry.Param.Builder paramBuilder = SmartContract.ABI.Entry.Param
            .newBuilder();
          paramBuilder.setIndexed(outputIndexed);
          paramBuilder.setName(outputName);
          paramBuilder.setType(outputType);
          entryBuilder.addOutputs(paramBuilder.build());
        }
      }

      entryBuilder.setType(getEntryType(type));
      entryBuilder.setPayable(payable);
      if (stateMutability != null) {
        entryBuilder.setStateMutability(getStateMutability(stateMutability));
      }

      abiBuilder.addEntrys(entryBuilder.build());
    }

    return abiBuilder.build();
  }

  public static Contract.UpdateSettingContract createUpdateSettingContract(byte[] owner,
    byte[] contractAddress, long consumeUserResourcePercent) {

    Contract.UpdateSettingContract.Builder builder = Contract.UpdateSettingContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    builder.setContractAddress(ByteString.copyFrom(contractAddress));
    builder.setConsumeUserResourcePercent(consumeUserResourcePercent);
    return builder.build();
  }

  public static Contract.UpdateEnergyLimitContract createUpdateEnergyLimitContract(
    byte[] owner,
    byte[] contractAddress, long originEnergyLimit) {

    Contract.UpdateEnergyLimitContract.Builder builder = Contract.UpdateEnergyLimitContract
      .newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    builder.setContractAddress(ByteString.copyFrom(contractAddress));
    builder.setOriginEnergyLimit(originEnergyLimit);
    return builder.build();
  }

  public static CreateSmartContract createContractDeployContract(String contractName,
    byte[] address,
    String ABI, String code, long value, long consumeUserResourcePercent, long originEnergyLimit,
    long tokenValue, String tokenId,
    String libraryAddressPair, String compilerVersion) {
    SmartContract.ABI abi = jsonStr2ABI(ABI);
    if (abi == null) {
      logger.error("abi is null");
      return null;
    }

    SmartContract.Builder builder = SmartContract.newBuilder();
    builder.setName(contractName);
    builder.setOriginAddress(ByteString.copyFrom(address));
    builder.setAbi(abi);
    builder.setConsumeUserResourcePercent(consumeUserResourcePercent)
      .setOriginEnergyLimit(originEnergyLimit);

    if (value != 0) {

      builder.setCallValue(value);
    }
    byte[] byteCode;
    if (null != libraryAddressPair) {
      byteCode = replaceLibraryAddress(code, libraryAddressPair, compilerVersion);
    } else {
      byteCode = Hex.decode(code);
    }

    builder.setBytecode(ByteString.copyFrom(byteCode));
    CreateSmartContract.Builder createSmartContractBuilder = CreateSmartContract.newBuilder();
    createSmartContractBuilder.setOwnerAddress(ByteString.copyFrom(address)).
      setNewContract(builder.build());
    if (tokenId != null && !tokenId.equalsIgnoreCase("") && !tokenId.equalsIgnoreCase("#")) {
      createSmartContractBuilder.setCallTokenValue(tokenValue).setTokenId(Long.parseLong(tokenId));
    }
    return createSmartContractBuilder.build();
  }

  private static byte[] replaceLibraryAddress(String code, String libraryAddressPair,
    String compilerVersion) {

    String[] libraryAddressList = libraryAddressPair.split("[,]");

    for (int i = 0; i < libraryAddressList.length; i++) {
      String cur = libraryAddressList[i];

      int lastPosition = cur.lastIndexOf(":");
      if (-1 == lastPosition) {
        throw new RuntimeException("libraryAddress delimit by ':'");
      }
      String libraryName = cur.substring(0, lastPosition);
      String addr = cur.substring(lastPosition + 1);
      String libraryAddressHex;
      try {
        libraryAddressHex = (new String(Hex.encode(WalletApi.decodeFromBase58Check(addr)),
          "US-ASCII")).substring(2);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);  // now ignore
      }

      String beReplaced;
      if (compilerVersion == null) {
        //old version
        String repeated = new String(new char[40 - libraryName.length() - 2]).replace("\0", "_");
        beReplaced = "__" + libraryName + repeated;
      } else if (compilerVersion.equalsIgnoreCase("v5")) {
        //0.5.4 version
        String libraryNameKeccak256 = ByteArray
          .toHexString(Hash.sha3(ByteArray.fromString(libraryName))).substring(0, 34);
        beReplaced = "__\\$" + libraryNameKeccak256 + "\\$__";
      } else {
        throw new RuntimeException("unknown compiler version.");
      }

      Matcher m = Pattern.compile(beReplaced).matcher(code);
      code = m.replaceAll(libraryAddressHex);
    }

    return Hex.decode(code);
  }

  public static Contract.TriggerSmartContract triggerCallContract(byte[] address,
    byte[] contractAddress,
    long callValue, byte[] data, long tokenValue, String tokenId) {
    Contract.TriggerSmartContract.Builder builder = Contract.TriggerSmartContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(address));
    builder.setContractAddress(ByteString.copyFrom(contractAddress));
    builder.setData(ByteString.copyFrom(data));
    builder.setCallValue(callValue);
    if (tokenId != null && tokenId != "") {
      builder.setCallTokenValue(tokenValue);
      builder.setTokenId(Long.parseLong(tokenId));
    }
    return builder.build();
  }

  public byte[] generateContractAddress(Transaction trx) {

    // get owner address
    // this address should be as same as the onweraddress in trx, DONNOT modify it
    byte[] ownerAddress = getAddress();

    // get tx hash
    byte[] txRawDataHash = Sha256Hash.of(trx.getRawData().toByteArray()).getBytes();

    // combine
    byte[] combined = new byte[txRawDataHash.length + ownerAddress.length];
    System.arraycopy(txRawDataHash, 0, combined, 0, txRawDataHash.length);
    System.arraycopy(ownerAddress, 0, combined, txRawDataHash.length, ownerAddress.length);

    return Hash.sha3omit12(combined);

  }

  public boolean updateSetting(byte[] contractAddress, long consumeUserResourcePercent)
    throws IOException, CipherException, CancelException {
    byte[] owner = getAddress();
    UpdateSettingContract updateSettingContract = createUpdateSettingContract(owner,
      contractAddress, consumeUserResourcePercent);

    TransactionExtention transactionExtention = rpcCli.updateSetting(updateSettingContract);
    if (transactionExtention == null || !transactionExtention.getResult().getResult()) {
      System.out.println("RPC create trx failed!");
      if (transactionExtention != null) {
        System.out.println("Code = " + transactionExtention.getResult().getCode());
        System.out
          .println("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
      }
      return false;
    }

    return processTransactionExtention(transactionExtention);

  }

  public boolean updateEnergyLimit(byte[] contractAddress, long originEnergyLimit)
    throws IOException, CipherException, CancelException {
    byte[] owner = getAddress();
    UpdateEnergyLimitContract updateEnergyLimitContract = createUpdateEnergyLimitContract(
      owner,
      contractAddress, originEnergyLimit);

    TransactionExtention transactionExtention = rpcCli
      .updateEnergyLimit(updateEnergyLimitContract);
    if (transactionExtention == null || !transactionExtention.getResult().getResult()) {
      System.out.println("RPC create trx failed!");
      if (transactionExtention != null) {
        System.out.println("Code = " + transactionExtention.getResult().getCode());
        System.out
          .println("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
      }
      return false;
    }

    return processTransactionExtention(transactionExtention);

  }

  public boolean deployContract(String contractName, String ABI, String code,
    long feeLimit, long value, long consumeUserResourcePercent, long originEnergyLimit,
    long tokenValue, String tokenId, String libraryAddressPair, String compilerVersion)
    throws IOException, CipherException, CancelException {
    byte[] owner = getAddress();
    CreateSmartContract contractDeployContract = createContractDeployContract(contractName, owner,
      ABI, code, value, consumeUserResourcePercent, originEnergyLimit, tokenValue, tokenId,
      libraryAddressPair, compilerVersion);

    TransactionExtention transactionExtention = rpcCli.deployContract(contractDeployContract);
    if (transactionExtention == null || !transactionExtention.getResult().getResult()) {
      System.out.println("RPC create trx failed!");
      if (transactionExtention != null) {
        System.out.println("Code = " + transactionExtention.getResult().getCode());
        System.out
          .println("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
      }
      return false;
    }

    TransactionExtention.Builder texBuilder = TransactionExtention.newBuilder();
    Transaction.Builder transBuilder = Transaction.newBuilder();
    Transaction.raw.Builder rawBuilder = transactionExtention.getTransaction().getRawData()
      .toBuilder();
    rawBuilder.setFeeLimit(feeLimit);
    transBuilder.setRawData(rawBuilder);
    for (int i = 0; i < transactionExtention.getTransaction().getSignatureCount(); i++) {
      ByteString s = transactionExtention.getTransaction().getSignature(i);
      transBuilder.setSignature(i, s);
    }
    for (int i = 0; i < transactionExtention.getTransaction().getRetCount(); i++) {
      Result r = transactionExtention.getTransaction().getRet(i);
      transBuilder.setRet(i, r);
    }
    texBuilder.setTransaction(transBuilder);
    texBuilder.setResult(transactionExtention.getResult());
    texBuilder.setTxid(transactionExtention.getTxid());
    transactionExtention = texBuilder.build();

    byte[] contractAddress = generateContractAddress(transactionExtention.getTransaction());
    System.out.println(
      "Your smart contract address will be: " + WalletApi.encode58Check(contractAddress));
    return processTransactionExtention(transactionExtention);

  }

  public String triggerContract(byte[] contractAddress, long callValue, byte[] data, long feeLimit,
    long tokenValue, String tokenId)
    throws IOException, CipherException, CancelException {
    byte[] owner = getAddress();
    Contract.TriggerSmartContract triggerContract = triggerCallContract(owner, contractAddress,
      callValue, data, tokenValue, tokenId);
    TransactionExtention transactionExtention = rpcCli.triggerContract(triggerContract);
    if (transactionExtention == null || !transactionExtention.getResult().getResult()) {
      System.out.println("RPC create call trx failed!");
      System.out.println("Code = " + transactionExtention.getResult().getCode());
      System.out
        .println("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
      return null;
    }

    Transaction transaction = transactionExtention.getTransaction();
    if (transaction.getRetCount() != 0 &&
      transactionExtention.getConstantResult(0) != null &&
      transactionExtention.getResult() != null) {
      byte[] result = transactionExtention.getConstantResult(0).toByteArray();
      System.out.println("message:" + transaction.getRet(0).getRet());
      System.out.println(":" + ByteArray
        .toStr(transactionExtention.getResult().getMessage().toByteArray()));
      System.out.println("Result:" + Hex.toHexString(result));
      return ByteArray.toHexString(transactionExtention.getTxid().toByteArray());
    }

    TransactionExtention.Builder texBuilder = TransactionExtention.newBuilder();
    Transaction.Builder transBuilder = Transaction.newBuilder();
    Transaction.raw.Builder rawBuilder = transactionExtention.getTransaction().getRawData()
      .toBuilder();
    rawBuilder.setFeeLimit(feeLimit);
    transBuilder.setRawData(rawBuilder);
    for (int i = 0; i < transactionExtention.getTransaction().getSignatureCount(); i++) {
      ByteString s = transactionExtention.getTransaction().getSignature(i);
      transBuilder.setSignature(i, s);
    }
    for (int i = 0; i < transactionExtention.getTransaction().getRetCount(); i++) {
      Result r = transactionExtention.getTransaction().getRet(i);
      transBuilder.setRet(i, r);
    }
    texBuilder.setTransaction(transBuilder);
    texBuilder.setResult(transactionExtention.getResult());
    texBuilder.setTxid(transactionExtention.getTxid());
    transactionExtention = texBuilder.build();

    return  processTransactionExt(transactionExtention);
  }

  public boolean checkTxInfo(String txId)  {
    try {
      System.out.println("wait 3s for check result. ");
      Thread.sleep(3_000);
      System.out.println("trx id: " + txId);
      Optional<TransactionInfo> transactionInfo = rpcCli.getTransactionInfoById(txId);
      TransactionInfo info = transactionInfo.get();
      if (info.getBlockTimeStamp() != 0L) {
        if (info.getResult().equals(TransactionInfo.code.SUCESS)) {
          return true;
        }
      }

      //retry
      int maxRetry = 3;
      for (int i = 0; i < maxRetry; i++) {
        Thread.sleep(1_000);
        System.out.println("will retry {} time(s): " + i+1);
        transactionInfo = rpcCli.getTransactionInfoById(txId);
        info = transactionInfo.get();
        if (info.getBlockTimeStamp() != 0L) {
          if (info.getResult().equals(TransactionInfo.code.SUCESS)) {
            return true;
          }
        }
      }
    } catch (InterruptedException e) {
      System.out.println("sleep error" + (e.getMessage()) );
      return false;
    }

    return false;
  }

  public static SmartContract getContract(byte[] address) {
    return rpcCli.getContract(address);
  }


  public boolean accountPermissionUpdate(byte[] owner, String permissionJson)
    throws CipherException, IOException, CancelException {
    Contract.AccountPermissionUpdateContract contract = createAccountPermissionContract(owner,
      permissionJson);
    TransactionExtention transactionExtention = rpcCli.accountPermissionUpdate(contract);
    return processTransactionExtention(transactionExtention);
  }

  private Permission json2Permission(JSONObject json) {
    Permission.Builder permissionBuilder = Permission.newBuilder();
    if (json.containsKey("type")) {
      int type = json.getInteger("type");
      permissionBuilder.setTypeValue(type);
    }
    if (json.containsKey("permission_name")) {
      String permission_name = json.getString("permission_name");
      permissionBuilder.setPermissionName(permission_name);
    }
    if (json.containsKey("threshold")) {
      long threshold = json.getLong("threshold");
      permissionBuilder.setThreshold(threshold);
    }
    if (json.containsKey("parent_id")) {
      int parent_id = json.getInteger("parent_id");
      permissionBuilder.setParentId(parent_id);
    }
    if (json.containsKey("operations")) {
      byte[] operations = ByteArray.fromHexString(json.getString("operations"));
      permissionBuilder.setOperations(ByteString.copyFrom(operations));
    }
    if (json.containsKey("keys")) {
      JSONArray keys = json.getJSONArray("keys");
      List<Key> keyList = new ArrayList<>();
      for (int i = 0; i < keys.size(); i++) {
        Key.Builder keyBuilder = Key.newBuilder();
        JSONObject key = keys.getJSONObject(i);
        String address = key.getString("address");
        long weight = key.getLong("weight");
        keyBuilder.setAddress(ByteString.copyFrom(WalletApi.decode58Check(address)));
        keyBuilder.setWeight(weight);
        keyList.add(keyBuilder.build());
      }
      permissionBuilder.addAllKeys(keyList);
    }
    return permissionBuilder.build();
  }

  public Contract.AccountPermissionUpdateContract createAccountPermissionContract(
    byte[] owner, String permissionJson) {
    Contract.AccountPermissionUpdateContract.Builder builder =
      Contract.AccountPermissionUpdateContract.newBuilder();

    JSONObject permissions = JSONObject.parseObject(permissionJson);
    JSONObject owner_permission = permissions.getJSONObject("owner_permission");
    JSONObject witness_permission = permissions.getJSONObject("witness_permission");
    JSONArray active_permissions = permissions.getJSONArray("active_permissions");

    if (owner_permission != null) {
      Permission ownerPermission = json2Permission(owner_permission);
      builder.setOwner(ownerPermission);
    }
    if (witness_permission != null) {
      Permission witnessPermission = json2Permission(witness_permission);
      builder.setWitness(witnessPermission);
    }
    if (active_permissions != null) {
      List<Permission> activePermissionList = new ArrayList<>();
      for (int j = 0; j < active_permissions.size(); j++) {
        JSONObject permission = active_permissions.getJSONObject(j);
        activePermissionList.add(json2Permission(permission));
      }
      builder.addAllActives(activePermissionList);
    }
    builder.setOwnerAddress(ByteString.copyFrom(owner));
    return builder.build();
  }


  public Transaction addTransactionSign(Transaction transaction)
    throws CipherException, IOException, CancelException {
    if (transaction.getRawData().getTimestamp() == 0) {
      transaction = TransactionUtils.setTimestamp(transaction);
    }
    transaction = TransactionUtils.setExpirationTime(transaction);
    System.out.println("Please input permission id.");
    transaction = TransactionUtils.setPermissionId(transaction);

    System.out.println("Please choose your key for sign.");
    WalletFile walletFile = selcetWalletFileE();
    System.out.println("Please input your password.");
    char[] password = Utils.inputPassword(false);
    byte[] passwd = org.tron.keystore.StringUtils.char2Byte(password);
    org.tron.keystore.StringUtils.clear(password);

    transaction = TransactionUtils.sign(transaction, this.getEcKey(walletFile, passwd), getCurrentChainId(), isMainChain());
    org.tron.keystore.StringUtils.clear(passwd);
    return transaction;
  }

}
