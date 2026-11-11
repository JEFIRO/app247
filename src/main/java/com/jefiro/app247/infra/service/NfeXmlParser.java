package com.jefiro.app247.infra.service;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Lê o XML da NF-e (nfeProc ou NFe) que o fornecedor envia junto com o DANFE. */
@Component
public class NfeXmlParser {
    private static final String NS = "http://www.portalfiscal.inf.br/nfe";
    private static final Set<String> AUTORIZADA = Set.of("100", "150");

    public record Nota(ChaveAcessoNfe chave, String numero, String serie, Instant dataEmissao,
                       String emitenteCnpj, String emitenteNome, String destinatarioDocumento,
                       BigDecimal valorTotal, List<Item> itens) {}

    public record Item(int numero, String codigo, String ean, String descricao, String ncm, String cest,
                       String cfop, String unidade, BigDecimal quantidade, BigDecimal valorUnitario,
                       BigDecimal valorTotal) {}

    public Nota ler(byte[] xml) {
        Document doc = documento(xml);
        Element infNFe = primeiro(doc.getDocumentElement(), "infNFe");
        if (infNFe == null) throw new IllegalArgumentException("O arquivo não é o XML de uma NF-e");
        ChaveAcessoNfe chave = ChaveAcessoNfe.parse(infNFe.getAttribute("Id"));

        Element prot = primeiro(doc.getDocumentElement(), "infProt");
        if (prot != null) {
            String cStat = texto(prot, "cStat");
            if (!AUTORIZADA.contains(cStat)) {
                throw new IllegalArgumentException("A NF-e não está autorizada na SEFAZ (cStat " + cStat + ")");
            }
        }

        Element ide = obrigatorio(infNFe, "ide");
        Element emit = obrigatorio(infNFe, "emit");
        Element dest = primeiro(infNFe, "dest");
        Element total = primeiro(infNFe, "ICMSTot");
        String destDoc = dest == null ? null : primeiroTexto(dest, "CNPJ", "CPF");

        List<Item> itens = new ArrayList<>();
        NodeList dets = infNFe.getElementsByTagNameNS(NS, "det");
        for (int i = 0; i < dets.getLength(); i++) {
            Element det = (Element) dets.item(i);
            Element prod = obrigatorio(det, "prod");
            itens.add(new Item(Integer.parseInt(det.getAttribute("nItem")), texto(prod, "cProd"),
                    gtin(texto(prod, "cEAN")), limitar(texto(prod, "xProd"), 180), texto(prod, "NCM"),
                    texto(prod, "CEST"), texto(prod, "CFOP"), texto(prod, "uCom").toUpperCase(),
                    decimal(prod, "qCom"), decimal(prod, "vUnCom"), decimal(prod, "vProd")));
        }
        if (itens.isEmpty()) throw new IllegalArgumentException("A NF-e não tem itens");

        String emissao = texto(ide, "dhEmi");
        return new Nota(chave, texto(ide, "nNF"), texto(ide, "serie"),
                emissao == null ? null : OffsetDateTime.parse(emissao).toInstant(),
                primeiroTexto(emit, "CNPJ", "CPF"), limitar(texto(emit, "xNome"), 180), destDoc,
                total == null ? itens.stream().map(Item::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                        : decimal(total, "vNF"), itens);
    }

    private Document documento(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // O XML vem de fora: nada de DTD nem entidades externas (XXE).
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Não foi possível ler o XML da NF-e", ex);
        }
    }

    private static Element primeiro(Element pai, String nome) {
        NodeList nodes = pai.getElementsByTagNameNS(NS, nome);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static Element obrigatorio(Element pai, String nome) {
        Element el = primeiro(pai, nome);
        if (el == null) throw new IllegalArgumentException("XML da NF-e sem o grupo <" + nome + ">");
        return el;
    }

    /** Só filhos diretos, para não pegar o CNPJ de outro grupo aninhado. */
    private static String texto(Element pai, String nome) {
        for (Node n = pai.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && nome.equals(el.getLocalName())) {
                String valor = el.getTextContent().trim();
                return valor.isEmpty() ? null : valor;
            }
        }
        return null;
    }

    private static String primeiroTexto(Element pai, String... nomes) {
        for (String nome : nomes) {
            String valor = texto(pai, nome);
            if (valor != null) return valor;
        }
        return null;
    }

    private static BigDecimal decimal(Element pai, String nome) {
        String valor = texto(pai, nome);
        if (valor == null) throw new IllegalArgumentException("XML da NF-e sem o campo <" + nome + ">");
        return new BigDecimal(valor);
    }

    private static String gtin(String valor) {
        if (valor == null || !valor.matches("\\d{8}|\\d{12,14}")) return null; // "SEM GTIN" ou vazio
        return valor;
    }

    private static String limitar(String valor, int max) {
        if (valor == null) return "";
        return valor.length() <= max ? valor : valor.substring(0, max);
    }
}
