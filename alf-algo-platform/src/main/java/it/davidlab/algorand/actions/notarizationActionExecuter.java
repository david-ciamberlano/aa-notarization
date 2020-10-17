package it.davidlab.algorand.actions;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.crypto.Address;
import com.algorand.algosdk.transaction.SignedTransaction;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.util.Encoder;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class notarizationActionExecuter extends ActionExecuterAbstractBase {

    private static Log logger = LogFactory.getLog(notarizationActionExecuter.class);

    private ServiceRegistry serviceRegistry;

    private String ALGOD_API_ADDR;
    private Integer ALGOD_PORT;
    private String ALGOD_API_TOKEN;
    private String ALGOD_EXPLORER_URL;

    private String ACC_PASSFRASE;
    private String ACC_ADDRESS;
    private String PS_API_KEY;

    private String[] headers_keys = {"x-api-key"};
    private String[] headers_values;


    @Override
    protected void executeImpl(Action action, NodeRef nodeRef) {

        NodeService nodeService = this.serviceRegistry.getNodeService();

        // Get document properties (metadata)
        Map<QName, Serializable> nodeProperties = nodeService.getProperties(nodeRef);

        // Get document content
        ContentReader reader = serviceRegistry.getContentService().getReader(nodeRef, ContentModel.PROP_CONTENT);
        InputStream originalInputStream = reader.getContentInputStream();

        // digest computation
        byte[] documentData;
        try {
            documentData = IOUtils.toByteArray(originalInputStream);
        } catch (IOException ex) {
            logger.error("notarization error: ", ex);
            throw new AlfrescoRuntimeException("Hash calculation error", ex);
        }

        if (documentData == null) {
            throw new AlfrescoRuntimeException("Document content is null");
        }

        String messageDigest = DigestUtils.sha256Hex(documentData);

        AlgoObject algoObject = new AlgoObject(messageDigest,
                nodeProperties.get(ContentModel.PROP_NAME).toString(),
                nodeProperties.get(ContentModel.PROP_CREATOR).toString(),
                (Date) (nodeProperties.get(ContentModel.PROP_CREATED)),
                nodeProperties.get(ContentModel.PROP_NODE_UUID).toString());

        // Build the json object
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create();
        String algoJsonObjct = gson.toJson(algoObject);


        String txId;
        Long txRound;
        String txDate;
        try {
            AlgodClient algoClient = new AlgodClient(this.ALGOD_API_ADDR, this.ALGOD_PORT, this.ALGOD_API_TOKEN);
            Address algoAddress = new Address(this.ACC_ADDRESS);
            Account algoAccount = new Account(this.ACC_PASSFRASE);
            TransactionParametersResponse params = algoClient.TransactionParams()
                    .execute(headers_keys,headers_values).body();
            Transaction txn = Transaction.PaymentTransactionBuilder()
                    .sender(algoAddress)
                    .note(algoJsonObjct.getBytes())
                    .amount(0)
                    .receiver(algoAddress)
                    .suggestedParams(params)
                    .build();

            SignedTransaction signedTxn = algoAccount.signTransaction(txn);
            byte[] encodedTxBytes = Encoder.encodeToMsgPack(signedTxn);

            //the following transaction needs an additional header
            String[] headers_keys_2 = ArrayUtils.add(headers_keys, "Content-Type");
            String[] headers_values_2 = ArrayUtils.add(headers_values, "application/x-binary");

            txId = algoClient.RawTransaction().rawtxn(encodedTxBytes)
                    .execute(headers_keys_2,headers_values_2).body().txId;
            this.waitForConfirmation(algoClient, txId, 5);

            txRound = algoClient.PendingTransactionInformation(txId)
                    .execute(headers_keys,headers_values).body().confirmedRound;

            Map<String, Object> block = algoClient.GetBlock(txRound)
                    .execute(headers_keys,headers_values).body().block;

            Integer timestamp = (Integer)block.get("ts");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            txDate = ZonedDateTime
                    .ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
                    .format(dateFormatter);

        } catch (Exception ex) {
            logger.error("Algorand Client creation Exception", ex);
            throw new AlfrescoRuntimeException("Client Exception", ex);
        }

        Map<QName, Serializable> aspectProperties  = new HashMap<>();
        aspectProperties.put(AlgoContentModel.txidQName, txId);
        aspectProperties.put(AlgoContentModel.addressQName, ACC_ADDRESS);
        aspectProperties.put(AlgoContentModel.blockQName, txRound);
        aspectProperties.put(AlgoContentModel.dateQName, txDate);
        aspectProperties.put(AlgoContentModel.hashQName, messageDigest);
        aspectProperties.put(AlgoContentModel.verUrlQName, ALGOD_EXPLORER_URL + txId);

        // if the aspect has already been added, throw an exception
        if (nodeService.hasAspect(nodeRef, AlgoContentModel.notarizedAspectQName)) {
            throw new AlfrescoRuntimeException("Error: Document already notarized");
        } else {
            // otherwise, add the aspect and set the properties
            nodeService.addAspect(nodeRef, AlgoContentModel.notarizedAspectQName,aspectProperties);
        }

    }


    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> list) {

    }

    private void waitForConfirmation(AlgodClient algoClient, String txId, int counter) throws Exception {
        long currentRound = algoClient.GetStatus()
                .execute(headers_keys,headers_values).body().lastRound;
        long maxRound = currentRound + counter;

        Long txConfirmedRound;
        do {
            algoClient.WaitForBlock(currentRound).execute(headers_keys,headers_values);
            txConfirmedRound = algoClient.PendingTransactionInformation(txId)
                    .execute(headers_keys,headers_values).body().confirmedRound;
            currentRound++;
        }
        while (((txConfirmedRound == null) || (txConfirmedRound <= 0)) && (currentRound <= maxRound));
    }


    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    public void setALGOD_API_ADDR(final String ALGOD_API_ADDR) {
        this.ALGOD_API_ADDR = ALGOD_API_ADDR;
    }

    public void setALGOD_PORT(final Integer ALGOD_PORT) {
        this.ALGOD_PORT = ALGOD_PORT;
    }

    public void setALGOD_API_TOKEN(final String ALGOD_API_TOKEN) {
        this.ALGOD_API_TOKEN = ALGOD_API_TOKEN;
    }

    public void setACC_PASSFRASE(final String ACC_PASSFRASE) {
        this.ACC_PASSFRASE = ACC_PASSFRASE;
    }

    public void setACC_ADDRESS(final String ACC_ADDRESS) {
        this.ACC_ADDRESS = ACC_ADDRESS;
    }

    public void setALGOD_EXPLORER_URL(final String ALGOD_EXPLORER_URL) {
        this.ALGOD_EXPLORER_URL = ALGOD_EXPLORER_URL;
    }

    public void setPS_API_KEY(final String PS_API_KEY) {
        this.PS_API_KEY = PS_API_KEY;
        this.headers_values = new String[] {PS_API_KEY};
    }
}
