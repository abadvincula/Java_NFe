/*
 * The MIT License
 *
 * Copyright 2021 abadv.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package br.com.swconsultoria.nfe.util;

import br.com.swconsultoria.nfe.exception.NfeException;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TEnviNFe;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TNfeProc;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TProtNFe;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author abadv
 */
public class Danfe {
    
    private final Document document;
    private final NodeList detalhes;
    private final NodeList pagamentos;
    private final Element identificacao;
    private final Element emitente;
    private final Element destinatario;
    private final Element total;
    private static int altura;
    
    private Danfe(Document doc) {
        this.document = doc;
        this.detalhes = doc.getElementsByTagName("det");
        this.pagamentos = doc.getElementsByTagName("pag");
        this.identificacao = (Element) doc.getElementsByTagName("ide").item(0);
        this.emitente = (Element) doc.getElementsByTagName("emit").item(0);
        this.destinatario = (doc.getElementsByTagName("dest").getLength() > 0) ? (Element) doc.getElementsByTagName("dest").item(0) : null;
        this.total = (Element) doc.getElementsByTagName("total").item(0);
        
        if(this.destinatario==null){
            altura = 460 + detalhes.getLength()*20;
        }else{
            altura = 490 + detalhes.getLength()*20;
        }
    }
    
    private static TNfeProc tNfeProc(TEnviNFe enviNfe, Object retorno) throws JAXBException, NfeException {
        TNfeProc nfeProc = new TNfeProc();
        nfeProc.setVersao("4.00");
        nfeProc.setNFe(enviNfe.getNFe().get(0));
        String xml = XmlNfeUtil.objectToXml(retorno);
        nfeProc.setProtNFe(XmlNfeUtil.xmlToObject(xml, TProtNFe.class));
        return nfeProc;
    }
    
    public static String getStringHtml(TEnviNFe enviNfe, Object retorno, String pathLogo) throws JAXBException, NfeException {

        Document doc = stringToDocumentXml(XmlNfeUtil.objectToXml(tNfeProc(enviNfe, retorno)));
        
        Danfe danfe = new Danfe(doc);
            
        return danfe.getStringHtml(pathLogo);
    }
    
    public static String getDanfceHtml(TEnviNFe enviNfe, Object retorno, String pathLogo, String pathHtml){
        try { 
            String strHtml = getStringHtml(enviNfe, retorno, pathLogo);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(pathHtml))) {
                bw.write(strHtml);
                bw.flush();
            }
            System.out.println("DANFCe HTML gerado com sucesso em: "+pathHtml);
        } catch (JAXBException | NfeException | IOException ex) {
            Logger.getLogger(Danfe.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        return pathHtml;
    }
    
    public static String getDanfcePdf(TEnviNFe enviNfe, Object retorno, String pathLogo, String pathPdf){
        try {
            String strHtml = getStringHtml(enviNfe, retorno, pathLogo);
            PdfWriter writer = new PdfWriter(pathPdf);
            PdfDocument pdfDocument = new PdfDocument(writer);
            PageSize pageSize = new PageSize(220, altura);
            pdfDocument.setDefaultPageSize(pageSize);
            ConverterProperties properties = new ConverterProperties();
            HtmlConverter.convertToPdf(strHtml, pdfDocument, properties);
            System.out.println("DANFCe PDF gerado com sucesso em: "+pathPdf);
        } catch (FileNotFoundException | JAXBException | NfeException ex) {
            Logger.getLogger(Danfe.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        return pathPdf;
    }
    
    private static Document stringToDocumentXml(String xmlString){
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = null;
        try{
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document doc = documentBuilder.parse(new InputSource(new StringReader(xmlString)));
            return doc;
        } 
        catch (IOException | ParserConfigurationException | SAXException e){
            e.printStackTrace();
        }
        return null;
    }
    
    public String getStringHtml(String pathLogo) {
        String stringHtml = getConfiguracoesHtml();
        stringHtml += getEmitente(pathLogo);
        stringHtml += getDetalhes();
        stringHtml += getPagamentos();
        stringHtml += getIdentificacao();
        stringHtml += getDestinatario();
        System.out.println("String HTML gerado com sucesso:\n"+stringHtml);
        return stringHtml;
    }
  
    /** 
     * Metodo que gera os estilos usados pela exportacao.
     * 
     * @return o estilo usado.
     */  
    private String getConfiguracoesHtml() {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html>\n<html lang='pt-br'>\n    <head>\n");
        sb.append("        <title>Visualizador DANFCe HTML</title>\n");
        sb.append("        <meta http-equiv='Content-Type' content='text/html; charset=UTF-8'/>\n");
        sb.append("        <style media='all'>\n");
        sb.append("            @page {margin: 0mm 0mm 0mm 0mm;}\n");
        sb.append("            body {margin: 0;font-family: sans-serif;font-size: 8px;}\n");
        sb.append("            div {margin-left: auto;margin-right: auto;width: 300px;background: #ffffff;}\n");
        sb.append("            table {border: 0px;width: 290px;}\n");
        sb.append("            td {text-align:center}\n");
        sb.append("            hr {margin-left: auto;margin-right: auto;border-style: dashed;border-width: 1px;}\n");
        sb.append("        </style>\n");
        sb.append("    </head>\n");
        sb.append("    <body>\n        <div>");
        return sb.toString();
    }  
  
    private String getEmitente(String pathLogo) {
        StringBuilder sb = new StringBuilder("\n            <table>\n                <tbody>");
        if(!pathLogo.endsWith("") && pathLogo != null)
            sb.append("\n                    <tr>\n                        <td><img src= 'data:image/png;base64,").append(getImageToBase64(pathLogo)).append("' width='100' alt='Red dot'/></td>\n                    </tr>");
        sb.append("\n                    <tr>\n                        <td><strong>").append(getValorTagElement(emitente, "xNome", false)).append("</strong></td>\n                    </tr>");
        sb.append("\n                    <tr>\n                        <td><strong>").append(getValorTagElement(emitente, "xFant", false)).append("</strong></td>\n                    </tr>");
        sb.append("\n                    <tr>\n                        <td>CNPJ: ").append(getValorTagElement(emitente, "CNPJ", false)).append("</td>\n                    </tr>");
        sb.append("\n                    <tr>\n                        <td>");
        sb.append(getValorTagElement(emitente, "xLgr", false)).append(", ");
        sb.append(getValorTagElement(emitente, "nro", false)).append(" ");
        String complemento = getValorTagElement(emitente, "xCpl", false);
        if(complemento==null){
            sb.append("<br />");
        }else{
            sb.append(complemento).append("<br />");
        }
        sb.append(getValorTagElement(emitente, "xBairro", false)).append(", ");
        sb.append(getValorTagElement(emitente, "xMun", false)).append("/");
        sb.append(getValorTagElement(emitente, "UF", false));
        sb.append("</td>\n                    </tr>");
        sb.append("\n                    <tr>\n                        <td><strong>DANFCE - DOCUMENTO AUXILIAR<br />NOTA FISCAL DE CONSUMIDOR ELETRONICA</strong></td>\n                    </tr>");
        sb.append("\n                </tbody>\n            </table><hr/>");
        return sb.toString();
    }  
  
    private String getDetalhes() {
        StringBuilder sb = new StringBuilder("\n            <table>\n                <tbody>");
        sb.append("\n                    <tr>\n                        <td style='text-align:left;width:80px'><strong>Codigo</strong></td>");
        sb.append("\n                        <td style='text-align:left;' colspan='2'><strong>Descricao</strong></td>");
        sb.append("\n                    <td>\n                    </td></tr>");
        sb.append("\n                        <tr><td style='text-align:right;width:80px'><strong>Qtde</strong></td>");
        sb.append("\n                            <td style='text-align:left;'><strong>Un</strong></td>");
        sb.append("\n                            <td style='text-align:right'><strong>Valor</strong></td>");
        sb.append("\n                            <td style='text-align:right'><strong>Total</strong></td>\n                        </tr>");
        sb.append("\n                        <tr><td colspan='4'><hr/></td>\n                    </tr>");
        for (int i = 0; i < detalhes.getLength(); i++) {
        //for (int i = 0; i < linhas; i++) {
            Element det = (Element) detalhes.item(i);
            sb.append("\n                        <tr>\n                            <td style='text-align:left;width:80px'>").append(getValorTagElement(det, "cProd", false)).append("</td>");
            sb.append("\n                            <td style='text-align:left;' colspan='3'>").append(getValorTagElement(det, "xProd", false)).append("</td>\n                        </tr>");
            sb.append("\n                        <tr>\n                            <td style='text-align:right;width:80px'>").append(formataNumero(Double.parseDouble(getValorTagElement(det, "qCom", false)), 1, 3, false, new Locale ("pt", "BR"))).append("</td>");
            sb.append("\n                            <td style='text-align:left;'>").append(getValorTagElement(det, "uCom", false)).append("</td>");
            sb.append("\n                            <td style='text-align:right'>").append(formataNumero(Double.parseDouble(getValorTagElement(det, "vUnCom", false)), 1, 3, true, new Locale ("pt", "BR"))).append("</td>");
            sb.append("\n                            <td style='text-align:right'>").append(formataNumero(Double.parseDouble(getValorTagElement(det, "vProd", false)), 1, 2, true, new Locale ("pt", "BR"))).append("</td>\n                        </tr>");
        }  
        sb.append("\n                </tbody>\n            </table><hr/>");
        return sb.toString();
    }  
  
    private String getPagamentos() {
        double outro = Double.parseDouble(getValorTagElement(total, "vOutro", false));
        double desc = Double.parseDouble(getValorTagElement(total, "vDesc", false));
        StringBuilder sb = new StringBuilder("\n            <table>\n                <tbody>");
        sb.append("\n                    <tr>\n                        <td style='text-align:left'>Qtd. Total de Itens</td>");
        sb.append("\n                        <td style='text-align:right'>").append(detalhes.getLength()).append("</td>\n                    </tr>");
        if (outro > 0) {
            sb.append("\n                    <tr>\n                        <td style='text-align:left'>Acrescimo</td>");
            sb.append("\n                        <td style='text-align:right'>").append(formataNumero(outro, 1, 2, true, new Locale ("pt", "BR"))).append("</td>\n                        </tr>");
        }
        if (desc > 0) {
            sb.append("\n                    <tr>\n                        <td style='text-align:left'>Desconto</td>");
            sb.append("\n                        <td style='text-align:right'>").append(formataNumero(desc, 1, 2, true, new Locale ("pt", "BR"))).append("</td>\n                        </tr>");
        }
        sb.append("\n                    <tr>\n                        <td style='text-align:left'>Valor Total R$</td>");
        sb.append("\n                        <td style='text-align:right'>").append(formataNumero(Double.parseDouble(getValorTagElement(total, "vNF", false)), 1, 2, true, new Locale ("pt", "BR"))).append("</td>\n                        </tr>");
        sb.append("\n                    <tr>\n                        <td style='text-align:left'><strong>FORMA PAGAMENTO</strong></td>");
        sb.append("\n                        <td style='text-align:right'><strong>VALOR PAGO</strong></td>\n                    </tr>");
        for (int i = 0; i < pagamentos.getLength(); i++) {
            Element pag = (Element) pagamentos.item(i);
            String tipo = getValorTagElement(pag, "tPag", false);
            switch (tipo) {
                case "01":
                    tipo = "Dinheiro";
                    break;
                case "02":
                    tipo = "Cheque";
                    break;
                case "03":
                    tipo = "Cartao de Credito";
                    break;
                case "04":
                    tipo = "Cartao de Debito";
                    break;
                case "05":
                    tipo = "Credito Loja";
                    break;
                case "10":
                    tipo = "Vale Alimentacao";
                    break;
                case "11":
                    tipo = "Vale Refeicao";
                    break;
                case "12":
                    tipo = "Vale Presente";
                    break;
                case "13":
                    tipo = "Vale Combustivel";
                    break;
                default:
                    tipo = "Outros";
                    break;
            }
            sb.append("\n                    <tr>\n                        <td style='text-align:left'>").append(tipo).append("</td>");
            sb.append("\n                        <td style='text-align:right'>").append(formataNumero(Double.parseDouble(getValorTagElement(pag, "vPag", false)), 1, 2, true, new Locale ("pt", "BR"))).append("</td>\n                    </tr>");
        }
        sb.append("\n                </tbody>\n            </table><hr/>");
        return sb.toString();
    }  
  
    private String getIdentificacao() {  
        String qrcode = getValorTagElement(document.getDocumentElement(), "qrCode", false);
        Date emissao = formatarData(getValorTagElement(identificacao, "dhEmi", false), "yyyy-MM-dd'T'HH:mm:ssXXX", new Locale ("pt", "BR"));
        Date recepcao = formatarData(getValorTagElement(document.getDocumentElement(), "dhRecbto", false), "yyyy-MM-dd'T'HH:mm:ssXXX", new Locale ("pt", "BR"));
        String chave = getValorTagElement(document.getDocumentElement(), "chNFe", false);
        String prot = getValorTagElement(document.getDocumentElement(), "nProt", false);
        String urlChave = getValorTagElement(document.getDocumentElement(), "urlChave", false);
        StringBuilder sb = new StringBuilder("\n            <table>\n                <tbody>");
        if (getValorTagElement(identificacao, "tpAmb", false).equals("2")) {
            sb.append("\n                    <tr>\n                        <td><strong>EMITIDA EM AMBIENTE DE HOMOLOGACAO<br />SEM VALOR FISCAL</strong></td>\n                    </tr>");
        }
        if (getValorTagElement(identificacao, "tpEmis", false).equals("9")) {
            sb.append("\n                    <tr>\n                        <td><strong>EMITIDA EM CONTIGENCIA</strong></td>\n                    </tr>");
        }
        sb.append("\n                    <tr>\n                        <td>Numero: ").append(formataTexto(getValorTagElement(identificacao, "nNF", false), "0", 9, false));
        sb.append(" Serie: ").append(formataTexto(getValorTagElement(identificacao, "serie", false), "0", 3, false));
        sb.append(" Emissao: ").append(formataDataHora(emissao, DateFormat.MEDIUM, new Locale ("pt", "BR"))).append("</td>\n                    </tr>");
        sb.append("\n                    <tr>\n                        <td>Consulte pela Chave de Acesso em<br />").append(urlChave).append("</td>\n                    </tr>");
        sb.append("\n                    <tr>\n                        <td>");
        for (int i = 0; i < 44; i += 4) {
            sb.append(chave.substring(i, i + 4)).append(" ");
        }
        sb.append("</td>\n                    </tr>");  
        sb.append("\n                    <tr>\n                        <td>Protocolo Autorizacao: ").append(prot).append(" ").append(formataDataHora(recepcao, DateFormat.MEDIUM, new Locale ("pt", "BR"))).append("</td>\n                    </tr>");
        if(qrcode!=null){
            sb.append("\n                    <tr>\n                        <td><img src= 'data:image/png;base64,").append(getQRCode(qrcode)).append("' alt='Red dot'/></td>\n                    </tr>");
            sb.append("\n                    <tr>\n                        <td style='text-align:left'>").append(getValorTagElement(document.getDocumentElement(), "infCpl", false).replace("#", "<br />")).append("</td>\n                    </tr>");
        }
        sb.append("\n                </tbody>\n            </table><hr/>");
        return sb.toString();
    }  
  
    private String getDestinatario() {
        StringBuilder sb = new StringBuilder("\n            <table>\n                <tbody>");
            if (destinatario == null) {
                sb.append("\n                    <tr>\n                        <td><strong>CONSUMIDOR NAO IDENTIFICADO</strong></td>\n                    </tr>");
            } else {
                String cpf = getValorTagElement(destinatario, "CPF", false);
                String cnpj = getValorTagElement(destinatario, "CNPJ", false);
                if (cpf!=null) {
                    sb.append("\n                    <tr>\n                        <td>CONSUMIDOR CPF: ").append(cpf).append("</td>\n                    </tr>");
                } else if (cnpj!=null) {
                    sb.append("\n                    <tr>\n                        <td>CONSUMIDOR CNPJ: ").append(cnpj).append("</td>\n                    </tr>");
                }
                sb.append("\n                    <tr>\n                        <td>NOME: ").append(getValorTagElement(destinatario, "xNome", false)).append("</td>\n                    </tr>");
                sb.append("\n                    <tr>\n                        <td>ENDERECO: ");
                sb.append(getValorTagElement(destinatario, "xLgr", false)).append(", ");
                sb.append(getValorTagElement(destinatario, "nro", false)).append(" ");
                sb.append(getValorTagElement(destinatario, "xCpl", false)).append("<br />");
                sb.append(getValorTagElement(destinatario, "xBairro", false)).append(", ");
                sb.append(getValorTagElement(destinatario, "xMun", false)).append("/");
                sb.append(getValorTagElement(destinatario, "UF", false));
                sb.append("<td>\n                    </tr>");
            }
        sb.append("\n                </tbody>\n            </table><hr/>");
        sb.append("\n        </div>\n    </body>\n</html>");
        return sb.toString();
    }
    
    private static String getValorTagElement(Element ele, String tag, boolean excecao) throws NullPointerException {  
        try {  
            return ele.getElementsByTagName(tag).item(0).getFirstChild().getNodeValue();  
        } catch (DOMException e) {  
            if (excecao) {  
                throw new NullPointerException("Nao achou a tag -> " + tag);  
            }  
            return "";  
        }  
    }
    
    public static String getImageToBase64(String pathLogo){
        byte[] fileContent = null;
        try {
            File fi = new File(pathLogo);
            fileContent = Files.readAllBytes(fi.toPath());
            
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
        String stringBase64 = Base64.getEncoder().encodeToString(fileContent);
        return (stringBase64);
    }

    /** 
    * Metodo que gera o QRCode. 
    * 
    * @param url a url para ser tranformada em imagem. 
    * @return a imagem em formato string base64. 
    */  
    public static String getQRCode(String url) {  
        try {
            
            // adicionar biblioteca zxing
            
            // seta as configuracoes  
//            Map<EncodeHintType, Object> hintMap = new HashMap<>();  
//            hintMap.put(EncodeHintType.CHARACTER_SET, "UTF-8");  
//            hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);  
//            hintMap.put(EncodeHintType.MARGIN, 1);  
//            // gera a imagem em bytes  
//            QRCodeWriter qrcode = new QRCodeWriter();  
//            BitMatrix matrix = qrcode.encode(url, BarcodeFormat.QR_CODE, 150, 150, hintMap);  
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();  
//            MatrixToImageWriter.writeToStream(matrix, "png", baos);
            return null ;//Base64.getEncoder().encodeToString(baos.toByteArray());  
        } catch (Exception ex) {  
            return null;  
        }  
    }  

    /** 
    * Metodo que formata o texto. 
    * 
    * @param texto o texto a ser formatado. 
    * @param caracter o caracter que sera repetido. 
    * @param tamanho o tamanho total do texto de resposta. 
    * @param direita a direcao onde colocar os caracteres. 
    * @return o texto formatado. 
    */  
    private static String formataTexto(String texto, String caracter, int tamanho, boolean direita) {  
        StringBuilder sb = new StringBuilder();  
        int fim = tamanho - texto.length();  
        for (int i = 0; i < fim; i++) {  
            sb.append(caracter);  
        }  
        String ret = direita ? texto + sb.toString() : sb.toString() + texto;  
        return ret;  
    }  

    /** 
    * Metodo que faz a formatacao de numeros com inteiros e fracoes. 
    * 
    * @param valor o valor a ser formatado. 
    * @param inteiros o minimo de inteiros, que serao completados com ZEROS se 
    * preciso. 
    * @param decimal o minimo de decimais, que serao completados com ZEROS se 
    * preciso. 
    * @param grupo se sera colocado separador de grupo de milhar. 
    * @param locale a localidade atual. 
    * @return uma String com o numero formatado. 
    */  
    private static String formataNumero(double valor, int inteiros, int decimal, boolean grupo, Locale locale) {  
        NumberFormat nf = NumberFormat.getIntegerInstance(locale);  
        nf.setMinimumIntegerDigits(inteiros);  
        nf.setMinimumFractionDigits(decimal);  
        nf.setMaximumFractionDigits(decimal);  
        nf.setGroupingUsed(grupo);  
        String ret = nf.format(valor);  
        return ret;  
    }  

    /** 
    * Metodo que transforma o texto em data usando um padrao definido. 
    * 
    * @param data a texto a ser transformado. 
    * @param padrao o padrao definido. 
    * @param locale a localidade atual. 
    * @return o objeto Date recuperado. 
    */  
    private static Date formatarData(String data, String padrao, Locale locale) {  
        Date ret = null;  
        if (data != null) {  
            try {  
                ret = new SimpleDateFormat(padrao, locale).parse(data);  
            } catch (Exception ex) {  
                ret = null;  
            }  
        }  
        return ret;  
    }  

    /** 
    * Metodo que formata a data e hora. 
    * 
    * @param data a data do tipo Date. 
    * @param formato o formado desejado usando algum padrao local. 
    * @param locale a localidade atual. 
    * @return a data formatada como solicidato. 
    */  
    private static String formataDataHora(Date data, int formato, Locale locale) {  
        //String dt = formataData(data, formato, locale);  
        //String hr = formataHora(data, formato, locale);  
        //String ret = dt + " " + hr;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
        return sdf.format(data);//ret;  
    }

    /** 
    * Metodo que formata a data e hora. 
    * 
    * @param data a data do tipo String. 
    * @param formatoData o formado desejado da data usando algum padrao local. 
    * @param formatoHora o formado desejado da hora usando algum padrao local. 
    * @param locale a localidade atual. 
    * @return a data e hora no objeto Date. 
    */  
    private static Date formataDataHora(String data, int formatoData, int formatoHora, Locale locale) {  
        Date ret = null;  
        if (data != null) {  
            try {  
                ret = DateFormat.getDateTimeInstance(formatoData, formatoHora, locale).parse(data);  
            } catch (ParseException ex) {  
                ret = null;  
            }  
        }  
        return ret;  
    } 
}
