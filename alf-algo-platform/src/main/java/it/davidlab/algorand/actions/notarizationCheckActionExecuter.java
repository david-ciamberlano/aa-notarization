package it.davidlab.algorand.actions;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.crypto.Address;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.xml.messaging.saaj.util.ByteInputStream;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Check a notarized
 */
public class notarizationCheckActionExecuter extends ActionExecuterAbstractBase {

    private static Log logger = LogFactory.getLog(notarizationCheckActionExecuter.class);

    private ServiceRegistry serviceRegistry;

    private String ALGOD_API_ADDR;
    private Integer ALGOD_PORT;
    private String ALGOD_API_TOKEN;

    private String ACC_PASSFRASE;
    private String ACC_ADDRESS;


    @Override
    protected void executeImpl(Action action, NodeRef nodeRef) {

        NodeService nodeService = serviceRegistry.getNodeService();

        AlgodClient algoClient = new AlgodClient(this.ALGOD_API_ADDR, this.ALGOD_PORT, this.ALGOD_API_TOKEN);

        // Read the document properties
        Map<QName, Serializable> nodeProperties = nodeService.getProperties(nodeRef);

        String propTxId = (String) nodeProperties.get(QName.createQName(
                AlgoContentModel.NAMESPACE_NOTARIZAZION_CONTENT_MODEL,
                AlgoContentModel.ASPECT_NRT_TXID));
        String propMessageDigest = (String) nodeProperties.get(QName.createQName(
                AlgoContentModel.NAMESPACE_NOTARIZAZION_CONTENT_MODEL,
                AlgoContentModel.ASPECT_NRT_HASH));

        // compute sha256 on document Content
        // Get document content
        ContentReader reader = this.serviceRegistry.getContentService().getReader(nodeRef, ContentModel.PROP_CONTENT);
        InputStream originalInputStream = reader.getContentInputStream();

        // digest computation
        byte[] documentData;
        try {
            documentData = IOUtils.toByteArray(originalInputStream);
        } catch (IOException ex) {
            logger.error("notarization error: ", ex);
            throw new AlfrescoRuntimeException("Hash calculation error", ex);
        }
        String messageDigest = DigestUtils.sha256Hex(documentData);

        // get info from algorand transaction
        AlgoObject algoObject;
        String txDate;
        try {
            PendingTransactionResponse tx = algoClient.PendingTransactionInformation(propTxId).execute().body();

            byte[] txNote = tx.txn.tx.note;
            String noteObject = new String(txNote);
            Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create();
            algoObject = gson.fromJson(noteObject, AlgoObject.class);

            Map<String, Object> block = algoClient.GetBlock(tx.confirmedRound).execute().body().block;
            Integer timestamp = (Integer)block.get("ts");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            txDate = ZonedDateTime
                    .ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
                    .format(dateFormatter);

        } catch (Exception ex) {
            logger.error("Algorand Client check Exception", ex);
            throw new AlfrescoRuntimeException("Document content is null", ex);
        }

        String validatedMsg;
        if (messageDigest.contentEquals(algoObject.getSha256hexContent())) {
            validatedMsg = "Notarization validated on " + txDate;
        }
        else {
            throw new AlfrescoRuntimeException("Hash not Validated");
        }

        nodeService.setProperty(nodeRef, ContentModel.PROP_DESCRIPTION, validatedMsg);

    }

    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> list) {

    }


    public void setServiceRegistry(final ServiceRegistry serviceRegistry) {
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
}
