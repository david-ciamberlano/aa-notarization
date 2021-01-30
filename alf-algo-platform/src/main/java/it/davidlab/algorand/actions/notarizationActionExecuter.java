package it.davidlab.algorand.actions;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.crypto.Address;
import com.algorand.algosdk.transaction.SignedTransaction;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.util.Encoder;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.common.Response;
import com.algorand.algosdk.v2.client.model.NodeStatusResponse;
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse;
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

    private Log logger = LogFactory.getLog(notarizationActionExecuter.class);

    private ServiceRegistry serviceRegistry;

    private String ALGOD_API_ADDR;
    private Integer ALGOD_PORT;
    private String ALGOD_API_TOKEN;
    private String ALGOD_EXPLORER_URL;

    private String ACC_PASSFRASE;
    private String ACC_ADDRESS;

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
            TransactionParametersResponse params = algoClient.TransactionParams().execute().body();
            Transaction txn = Transaction.PaymentTransactionBuilder()
                    .sender(algoAddress)
                    .note(algoJsonObjct.getBytes())
                    .amount(0)
                    .receiver(algoAddress)
                    .suggestedParams(params)
                    .build();

            SignedTransaction signedTxn = algoAccount.signTransaction(txn);
            byte[] encodedTxBytes = Encoder.encodeToMsgPack(signedTxn);

            txId = algoClient.RawTransaction().rawtxn(encodedTxBytes).execute().body().txId;
            waitForConfirmation(algoClient, txId, 5);

            txRound = algoClient.PendingTransactionInformation(txId).execute().body().confirmedRound;

            Map<String, Object> block = algoClient.GetBlock(txRound).execute().body().block;

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

    public void waitForConfirmation(AlgodClient client, String txId, int timeout) throws Exception {

        Long txConfirmedRound = -1L;
        Response<NodeStatusResponse> statusResponse = client.GetStatus().execute();

        long lastRound;
        if (statusResponse.isSuccessful()) {
            lastRound = statusResponse.body().lastRound + 1L;
        }
        else {
            throw new IllegalStateException("Cannot get node status");
        }

        long maxRound = lastRound + timeout;

        for (long currentRound = lastRound; currentRound < maxRound; currentRound++) {
            Response<PendingTransactionResponse> response = client.PendingTransactionInformation(txId).execute();

            if (response.isSuccessful()) {
                txConfirmedRound = response.body().confirmedRound;
                if (txConfirmedRound == null) {
                    if (!client.WaitForBlock(currentRound).execute().isSuccessful()) {
                        throw new Exception();
                    }
                }
                else {
                    return;
                }
            } else {
                throw new IllegalStateException("The transaction has been rejected");
            }
        }

        throw new IllegalStateException("Transaction not confirmed after " + timeout + " rounds!");
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

}
