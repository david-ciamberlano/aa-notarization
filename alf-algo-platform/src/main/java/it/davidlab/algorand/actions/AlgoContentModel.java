package it.davidlab.algorand.actions;

import org.alfresco.service.namespace.QName;

public interface AlgoContentModel {

    String NAMESPACE_NOTARIZAZION_CONTENT_MODEL = "http://www.davidlab.it/model/notarization/1.0";
    String ASPECT_NRT_NOTARIZED = "notarized";
    String ASPECT_NRT_TXID = "txid";
    String ASPECT_NRT_ADDRESS = "address";
    String ASPECT_NRT_DATE = "datetime";
    String ASPECT_NRT_BLOCK = "block";
    String ASPECT_NRT_HASH = "hash";
    String ASPECT_NRT_VERURL = "verificationUrl";

    QName notarizedAspectQName = QName.createQName(NAMESPACE_NOTARIZAZION_CONTENT_MODEL, ASPECT_NRT_NOTARIZED);
    QName txidQName = QName.createQName(NAMESPACE_NOTARIZAZION_CONTENT_MODEL, ASPECT_NRT_TXID);
    QName addressQName = QName.createQName(NAMESPACE_NOTARIZAZION_CONTENT_MODEL, ASPECT_NRT_ADDRESS);
    QName dateQName = QName.createQName(NAMESPACE_NOTARIZAZION_CONTENT_MODEL, ASPECT_NRT_DATE);
    QName blockQName = QName.createQName(NAMESPACE_NOTARIZAZION_CONTENT_MODEL, ASPECT_NRT_BLOCK);
    QName hashQName = QName.createQName(NAMESPACE_NOTARIZAZION_CONTENT_MODEL, ASPECT_NRT_HASH);
    QName verUrlQName = QName.createQName(NAMESPACE_NOTARIZAZION_CONTENT_MODEL, ASPECT_NRT_VERURL);




}
