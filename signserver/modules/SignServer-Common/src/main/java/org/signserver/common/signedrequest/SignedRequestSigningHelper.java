/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.signserver.common.signedrequest;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.DefaultJwtBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.signserver.common.RequestContext;

/**
 * Helper doing as much of the stuff for the signed request as possible.
 * 
 * Current spec for the format:
 * Request metadata property named "SIGNED_REQUEST"
 * - Value: base64 encoded  CMS SignedData structure with
 *          encapsulated content: SHA-256 hash of requestData
 * 
 * TODO: Spec does not cover hashing of workerName/workerId and request metadata etc.
 *
 * @author user
 */
public class SignedRequestSigningHelper {
    
    private static final Logger LOG = Logger.getLogger(SignedRequestSigningHelper.class);
    
    public static final String METADATA_PROPERTY_SIGNED_REQUEST = "SIGNED_REQUEST";
    
    private static final String DIGEST_ALGORITHM = "SHA-256"; // XXX hardcoded, but should use what's in the request signature

    /**
     * Adds the SIGNED_REQUEST request metadata to the passed in metadata
     * 
     * @param digest the digest
     * @param metadata the metadata
     * @param fileName the file name (if any)
     * @param workerName the worker name (if any)
     * @param workerId the worker ID (if any)
     * @param signatureAlgorithm the algorithm to use
     * @param privateKey private key to use for signing
     * @param certChain cert chain for the signer
     * @throws SignedRequestException in case of failure creating the signature
     */
    public static void addRequestSignature(final byte[] digest,
                                           final Map<String, String> metadata,
                                           final String fileName,
                                           final String workerName,
                                           final Integer workerId,
                                           final String signatureAlgorithm,
                                           final PrivateKey privateKey,
                                           final List<Certificate> certChain)
            throws SignedRequestException {
        final String signature =
                SignedRequestSigningHelper.createSignedRequest(digest, metadata,
                                                               fileName,
                                                               workerName,
                                                               workerId,
                                                               privateKey,
                                                               signatureAlgorithm,
                                                               null, certChain);
        metadata.put(SignedRequestSigningHelper.METADATA_PROPERTY_SIGNED_REQUEST,
                     signature);
    }
    
    /**
     * Constructs the SIGNED_REQUEST request metadata property value.
     *
     * @param requestDataDigest the digest
     * @param metadata the metadata
     * @param fileName the file name field (if any)
     * @param workerName the worker name field (if any)
     * @param workerId the worker id field (if any(
     * @param signKey private key to sign with
     * @param signatureAlgorithm the algorithm to use
     * @param provider provider for the signature
     * @param certificateChain for the signer
     * @return the String encoding of the SIGNED_REQUEST property
     * @throws SignedRequestException in case of failure creating the signature
     */
    public static String createSignedRequest(byte[] requestDataDigest, Map<String, String> metadata, String fileName, String workerName, Integer workerId, PrivateKey signKey, String signatureAlgorithm, Provider provider, List<Certificate> certificateChain) throws SignedRequestException {
        try {
            LOG.debug(">createSignedRequest");
            return createSignedJwt(createContentToBeSigned(requestDataDigest, metadata, fileName, workerName, workerId),
                                   signKey, signatureAlgorithm,
                                   provider, certificateChain);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | IOException | CertificateEncodingException ex) {
            throw new SignedRequestException("Failed to sign signature request", ex);
        }
    }

    private static String createSignedJwt(Properties properties, PrivateKey signKey, String signatureAlgorithm, Provider provider, List<Certificate> certificateChain) throws SignedRequestException, CertificateEncodingException {
        LOG.debug(">createSignedJwt");

        final JwtBuilder builder =
                new DefaultJwtBuilder()
                        .setHeaderParam("typ", "JWT") // TODO: type...
                        .setHeaderParam("x5c", convertChain(certificateChain))
                        .addClaims(convertPropertiesToClaims(properties))
                        .signWith(signKey,
                                  SignatureAlgorithm.forJcaName(signatureAlgorithm));

        return builder.compact();
    }

    private static List<String> convertChain(final List<Certificate> chain)
            throws CertificateEncodingException {
        final List<String> result = new LinkedList<>();

        for (final Certificate cert : chain) {
            result.add(Base64.toBase64String(cert.getEncoded()));
        }

        return result;
    }

    private static Map<String, Object> convertPropertiesToClaims(final Properties properties) {
        final Map<String, Object> result = new HashMap<>();
        
        for (final String key : properties.stringPropertyNames()) {
            result.put(key, properties.get(key));
        }

        return result;
    }
    
    private static String createSignedCms(Properties properties, PrivateKey signKey, String signatureAlgorithm, Provider provider, List<Certificate> certificateChain) throws SignedRequestException {
        try {
            LOG.debug(">createSignedCms");

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            properties.store(bout, null);
            final byte[] contentToBeSigned = bout.toByteArray();
            
            final CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            final JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder(signatureAlgorithm);
            if (provider != null) {
                csBuilder.setProvider(provider);
            }
            final ContentSigner contentSigner = csBuilder.build(signKey);
            final JcaSignerInfoGeneratorBuilder siBuilder = new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().setProvider("BC").build());
            final SignerInfoGenerator sig = siBuilder.build(contentSigner, (X509Certificate) certificateChain.get(0));
            
            generator.addSignerInfoGenerator(sig);
            generator.addCertificates(new JcaCertStore(certificateChain));
            
            // Generate the signature
            CMSSignedData signedData = generator.generate(new CMSProcessableByteArray(contentToBeSigned), true);
            
            final String result = Base64.toBase64String(signedData.getEncoded());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created signed request: " + result);
            }
            return result;
        } catch (OperatorCreationException | CMSException | CertificateEncodingException | IOException ex) {
            throw new SignedRequestException("Failed to sign signature request", ex);
        }
    }
        
    private static Properties createContentToBeSigned(byte[] requestDataDigest, Map<String, String> metadata, String fileName, String workerName, Integer workerId) throws IOException, NoSuchAlgorithmException, NoSuchProviderException {
        Properties properties = new Properties();
       
        properties.put("data", Hex.toHexString(requestDataDigest));
        ArrayList<String> metaKeys = new ArrayList<>(metadata.keySet());
        for (String metaKey : metaKeys) {
            if (!metaKey.equals(METADATA_PROPERTY_SIGNED_REQUEST)) {
                properties.put("meta." + metaKey, Hex.toHexString(hash(metadata.get(metaKey))));
            }
        }
        if (fileName != null) {
            properties.put(RequestContext.FILENAME, Hex.toHexString(hash(fileName)));
        }

        if (workerName != null) {
            properties.put("workerName", Hex.toHexString(hash(workerName)));
        }
        if (workerId != null) {
            properties.put("workerId", Hex.toHexString(hash(String.valueOf(workerId))));
        }
        
        return properties;
    }
    
    public static byte[] hash(String value) throws NoSuchAlgorithmException, NoSuchProviderException {
        MessageDigest md = MessageDigest.getInstance(DIGEST_ALGORITHM, "BC");
        
        return md.digest(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
    }

}
