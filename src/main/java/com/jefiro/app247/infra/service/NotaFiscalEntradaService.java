package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.notafiscal.ChaveAcessoResponse;
import com.jefiro.app247.domain.model.dto.notafiscal.NotaFiscalEntradaResponse;
import com.jefiro.app247.domain.model.dto.notafiscal.NotaFiscalItemRequest;
import com.jefiro.app247.domain.model.enum_type.*;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Entrada de mercadoria pela NF-e do fornecedor: lê o XML, casa cada item com o catálogo
 * (EAN ou código do fornecedor), deixa o administrador revisar e, ao confirmar, cria os
 * produtos novos e lança ENTRADA no estoque central.
 */
@Service
public class NotaFiscalEntradaService {
    private static final long MAX_XML_BYTES = 2 * 1024 * 1024;
    /** Unidades de embalagem em que a quantidade da nota raramente é a de venda. */
    private static final Set<String> EMBALAGENS = Set.of("CX", "CXA", "FD", "FDO", "FARDO", "PCT", "PAC",
            "PACOTE", "DZ", "DZA", "KIT", "EMB", "DP", "BD", "BDJ", "SC", "SACO");

    @Autowired private NfeXmlParser parser;
    @Autowired private NfeDistribuicaoGateway sefaz;
    @Autowired private NotaFiscalEntradaRepository notaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ProdutoCodigoBarrasRepository barcodeRepository;
    @Autowired private EstoqueEmpresaRepository estoqueRepository;
    @Autowired private MovimentacaoEstoqueRepository movimentoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private AuditLogService auditLogService;
    @Autowired private ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public ChaveAcessoResponse consultarChave(String valor) {
        String empresaId = EmpresaContext.require();
        ChaveAcessoNfe chave = ChaveAcessoNfe.parse(valor);
        Empresa empresa = empresaRepository.getReferenceById(empresaId);
        var concluida = notaRepository.findFirstByEmpresaIdAndChaveAndStatusOrderByCreatedAtDesc(
                empresaId, chave.chave(), NotaFiscalEntradaStatus.CONCLUIDA);
        var rascunho = notaRepository.findFirstByEmpresaIdAndChaveAndStatusOrderByCreatedAtDesc(
                empresaId, chave.chave(), NotaFiscalEntradaStatus.VALIDADA);
        var nota = concluida.or(() -> rascunho).orElse(null);
        boolean sefazDisponivel = sefaz.disponivel(empresa);
        String mensagem;
        if (concluida.isPresent()) mensagem = "Esta NF-e já foi importada";
        else if (rascunho.isPresent()) mensagem = "Esta NF-e já foi lida e aguarda confirmação";
        else if (sefazDisponivel) mensagem = "Pronta para buscar na SEFAZ";
        else mensagem = "Envie o XML desta NF-e para ver os itens";
        return new ChaveAcessoResponse(chave.chave(), chave.uf(), chave.anoMes(), chave.cnpjEmitente(),
                chave.modelo(), chave.serie(), chave.numero(), nota == null ? null : nota.getId(),
                nota == null ? null : nota.getStatus().name(), sefazDisponivel, mensagem);
    }

    @Transactional
    public NotaFiscalEntradaResponse importarXml(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Selecione o XML da NF-e");
        if (file.getSize() > MAX_XML_BYTES) throw new IllegalArgumentException("O XML excede o limite de 2 MB");
        String nome = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!nome.endsWith(".xml")) throw new IllegalArgumentException("Formato não suportado. Envie o arquivo .xml da NF-e");
        return registrar(file.getBytes(), "XML", null);
    }

    @Transactional
    public NotaFiscalEntradaResponse importarPorChave(String valor) {
        String empresaId = EmpresaContext.require();
        ChaveAcessoNfe chave = ChaveAcessoNfe.parse(valor);
        Empresa empresa = empresaRepository.getReferenceById(empresaId);
        if (!sefaz.disponivel(empresa)) {
            throw new IllegalStateException("Busca na SEFAZ indisponível: cadastre o certificado A1 da Empresa ou envie o XML");
        }
        byte[] xml = sefaz.baixarXml(empresa, chave.chave())
                .orElseThrow(() -> new NoSuchElementException("A SEFAZ não retornou o XML desta NF-e"));
        return registrar(xml, "SEFAZ", chave.chave());
    }

    @Transactional(readOnly = true)
    public NotaFiscalEntradaResponse buscar(String id) {
        return NotaFiscalEntradaResponse.from(nota(id));
    }

    @Transactional
    public NotaFiscalEntradaResponse ajustarItem(String notaId, String itemId, NotaFiscalItemRequest request) {
        String empresaId = EmpresaContext.require();
        NotaFiscalEntrada nota = notaRepository.findForUpdate(notaId, empresaId)
                .orElseThrow(() -> new NoSuchElementException("NF-e não encontrada"));
        if (nota.getStatus() != NotaFiscalEntradaStatus.VALIDADA) {
            throw new IllegalStateException("Esta NF-e já foi importada");
        }
        NotaFiscalEntradaItem item = nota.getItens().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Item da NF-e não encontrado"));
        if (request.acao() != null) item.setAcao(NotaFiscalItemAcao.valueOf(request.acao().toUpperCase(Locale.ROOT)));
        if (item.getAcao() == NotaFiscalItemAcao.VINCULAR) {
            if (request.produtoId() != null) {
                item.setProduto(produtoRepository.findByIdProdutoAndEmpresaId(request.produtoId(), empresaId)
                        .orElseThrow(() -> new IllegalArgumentException("Produto não pertence à empresa atual")));
            }
        } else {
            item.setProduto(null);
            if (request.nomeProduto() != null) item.setNomeProduto(limitar(request.nomeProduto().trim(), 180));
            if (request.precoVenda() != null) {
                if (request.precoVenda().signum() <= 0) throw new IllegalArgumentException("O preço de venda deve ser maior que zero");
                item.setPrecoVenda(MoneyPolicy.persistence(request.precoVenda()));
            }
            if (request.categoria() != null) {
                item.setCategoria(ProdutoCategoria.valueOf(request.categoria().toUpperCase(Locale.ROOT)));
            }
        }
        if (request.fatorConversao() != null) {
            BigDecimal fator = EstoqueEmpresaService.quantidade(request.fatorConversao());
            if (fator.signum() <= 0) throw new IllegalArgumentException("A quantidade por embalagem deve ser maior que zero");
            item.setFatorConversao(fator);
        }
        avaliar(item);
        notaRepository.saveAndFlush(nota);
        return NotaFiscalEntradaResponse.from(nota);
    }

    @Transactional
    public NotaFiscalEntradaResponse confirmar(String id) {
        String empresaId = EmpresaContext.require();
        NotaFiscalEntrada nota = notaRepository.findForUpdate(id, empresaId)
                .orElseThrow(() -> new NoSuchElementException("NF-e não encontrada"));
        if (nota.getStatus() == NotaFiscalEntradaStatus.CONCLUIDA) return NotaFiscalEntradaResponse.from(nota);
        nota.getItens().forEach(this::avaliar);
        if (nota.getItens().stream().anyMatch(i -> i.getStatus() == NotaFiscalItemStatus.PENDENTE)) {
            throw new IllegalStateException("Resolva os itens pendentes antes de confirmar a NF-e");
        }
        if (notaRepository.findFirstByEmpresaIdAndChaveAndStatusOrderByCreatedAtDesc(
                empresaId, nota.getChave(), NotaFiscalEntradaStatus.CONCLUIDA).isPresent()) {
            throw new IllegalStateException("Esta NF-e já foi importada");
        }

        Empresa empresa = nota.getEmpresa();
        User usuario = usuarioAtual(empresaId);
        String motivo = limitar("NF-e " + nota.getNumero() + " · " + nota.getEmitenteNome(), 300);
        // Dois itens da mesma nota com o mesmo EAN viram um produto só.
        Map<String, Produto> criadosPorEan = new HashMap<>();
        int criados = 0;
        for (NotaFiscalEntradaItem item : nota.getItens()) {
            Produto produto = item.getProduto();
            if (item.getAcao() == NotaFiscalItemAcao.CRIAR) {
                produto = item.getEan() == null ? null : criadosPorEan.get(item.getEan());
                if (produto == null) produto = existente(empresaId, item).orElse(null);
                if (produto == null) {
                    produto = criarProduto(empresa, nota, item);
                    criados++;
                }
                if (item.getEan() != null) criadosPorEan.putIfAbsent(item.getEan(), produto);
                item.setProduto(produto);
            }
            darEntrada(empresa, produto, item, nota, usuario, motivo);
            item.setStatus(NotaFiscalItemStatus.IMPORTADO);
            item.setMensagem("Entrada de " + item.getQuantidadeEntrada().stripTrailingZeros().toPlainString()
                    + " em " + produto.getNome());
        }
        nota.setStatus(NotaFiscalEntradaStatus.CONCLUIDA);
        nota.setChaveConcluida(nota.getChave());
        nota.setProdutosCriados(criados);
        nota.setEntradasLancadas(nota.getItens().size());
        nota.setProcessedAt(Instant.now());
        notaRepository.saveAndFlush(nota);
        auditLogService.record(empresa, "NFE_ENTRADA_CONFIRMADA", "NotaFiscalEntrada", nota.getId(),
                Map.of("status", "VALIDADA"), Map.of("status", "CONCLUIDA", "produtosCriados", criados,
                        "entradas", nota.getItens().size()), Map.of("chave", nota.getChave()));
        return NotaFiscalEntradaResponse.from(nota);
    }

    private NotaFiscalEntradaResponse registrar(byte[] xml, String origem, String chaveEsperada) {
        String empresaId = EmpresaContext.require();
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new NoSuchElementException("Empresa não encontrada"));
        NfeXmlParser.Nota lida = parser.ler(xml);
        String chave = lida.chave().chave();
        if (chaveEsperada != null && !chaveEsperada.equals(chave)) {
            throw new IllegalArgumentException("O XML recebido é de outra NF-e");
        }
        String cnpjEmpresa = digitos(empresa.getCnpj());
        if (lida.destinatarioDocumento() == null || !lida.destinatarioDocumento().equals(cnpjEmpresa)) {
            throw new IllegalArgumentException("Esta NF-e não foi emitida para o CNPJ da sua empresa");
        }
        if (notaRepository.findFirstByEmpresaIdAndChaveAndStatusOrderByCreatedAtDesc(
                empresaId, chave, NotaFiscalEntradaStatus.CONCLUIDA).isPresent()) {
            throw new IllegalStateException("Esta NF-e já foi importada");
        }
        // Reenviar o mesmo XML retoma a revisão em andamento em vez de duplicá-la.
        var rascunho = notaRepository.findFirstByEmpresaIdAndChaveAndStatusOrderByCreatedAtDesc(
                empresaId, chave, NotaFiscalEntradaStatus.VALIDADA);
        if (rascunho.isPresent()) return NotaFiscalEntradaResponse.from(rascunho.get());

        NotaFiscalEntrada nota = new NotaFiscalEntrada();
        nota.setEmpresa(empresa);
        nota.setChave(chave);
        nota.setModelo(lida.chave().modelo());
        nota.setNumero(lida.numero() == null ? lida.chave().numero() : lida.numero());
        nota.setSerie(lida.serie() == null ? lida.chave().serie() : lida.serie());
        nota.setEmitenteCnpj(lida.emitenteCnpj() == null ? lida.chave().cnpjEmitente() : lida.emitenteCnpj());
        nota.setEmitenteNome(lida.emitenteNome());
        nota.setDestinatarioDocumento(lida.destinatarioDocumento());
        nota.setDataEmissao(lida.dataEmissao());
        nota.setValorTotal(lida.valorTotal().setScale(2, RoundingMode.HALF_UP));
        nota.setOrigem(origem);
        nota.setHashXml(sha256(xml));
        nota.setStatus(NotaFiscalEntradaStatus.VALIDADA);
        nota.setCreatedBy(usuarioAtual(empresaId));
        for (NfeXmlParser.Item lido : lida.itens()) nota.getItens().add(item(empresa, nota, lido));
        nota = notaRepository.saveAndFlush(nota);
        auditLogService.record(empresa, "NFE_ENTRADA_LIDA", "NotaFiscalEntrada", nota.getId(), null,
                Map.of("chave", chave, "itens", nota.getItens().size(), "origem", origem), Map.of());
        return NotaFiscalEntradaResponse.from(nota);
    }

    private NotaFiscalEntradaItem item(Empresa empresa, NotaFiscalEntrada nota, NfeXmlParser.Item lido) {
        NotaFiscalEntradaItem item = new NotaFiscalEntradaItem();
        item.setEmpresa(empresa);
        item.setNota(nota);
        item.setNumeroItem(lido.numero());
        item.setCodigoFornecedor(limitar(lido.codigo() == null ? String.valueOf(lido.numero()) : lido.codigo(), 60));
        item.setEan(lido.ean());
        item.setDescricao(lido.descricao());
        item.setNcm(lido.ncm());
        item.setCest(lido.cest());
        item.setCfop(lido.cfop());
        item.setUnidadeComercial(limitar(lido.unidade() == null ? "UN" : lido.unidade(), 6));
        item.setQuantidade(lido.quantidade());
        item.setValorUnitario(lido.valorUnitario());
        item.setValorTotal(lido.valorTotal().setScale(2, RoundingMode.HALF_UP));
        item.setUnidadeMedida(unidadeVenda(item.getUnidadeComercial()));
        String codigoInterno = codigoInterno(nota.getEmitenteCnpj(), item.getCodigoFornecedor());
        Optional<Produto> produto = item.getEan() == null ? Optional.empty()
                : barcodeRepository.findByEmpresaIdAndCodigoBarrasAndAtivoTrue(empresa.getId(), item.getEan())
                        .map(ProdutoCodigoBarras::getProduto);
        if (produto.isEmpty()) produto = produtoRepository.findByCodigoInternoAndEmpresaId(codigoInterno, empresa.getId());
        if (produto.isPresent()) {
            item.setAcao(NotaFiscalItemAcao.VINCULAR);
            item.setProduto(produto.get());
        } else {
            item.setAcao(NotaFiscalItemAcao.CRIAR);
            item.setNomeProduto(lido.descricao());
            item.setCategoria(categoriaPorNcm(lido.ncm()));
        }
        avaliar(item);
        return item;
    }

    /** Recalcula o status do item a partir do que já foi decidido na revisão. */
    private void avaliar(NotaFiscalEntradaItem item) {
        if (item.getStatus() == NotaFiscalItemStatus.IMPORTADO) return;
        String pendencia = null;
        if (item.getAcao() == NotaFiscalItemAcao.VINCULAR) {
            if (item.getProduto() == null) pendencia = "Escolha o produto do catálogo";
        } else {
            if (item.getNomeProduto() == null || item.getNomeProduto().isBlank()) pendencia = "Informe o nome do produto";
            else if (item.getPrecoVenda() == null) pendencia = "Informe o preço de venda";
            else if (item.getCategoria() == null) pendencia = "Escolha a categoria";
        }
        if (pendencia != null) {
            item.setStatus(NotaFiscalItemStatus.PENDENTE);
            item.setMensagem(pendencia);
        } else if (EMBALAGENS.contains(item.getUnidadeComercial())
                && item.getFatorConversao().compareTo(BigDecimal.ONE) == 0) {
            item.setStatus(NotaFiscalItemStatus.AVISO);
            item.setMensagem("Nota em " + item.getUnidadeComercial() + ": confira quantas unidades vêm em cada embalagem");
        } else {
            item.setStatus(NotaFiscalItemStatus.PRONTO);
            item.setMensagem(item.getAcao() == NotaFiscalItemAcao.VINCULAR
                    ? "Soma ao estoque de " + item.getProduto().getNome() : "Produto novo");
        }
    }

    private Optional<Produto> existente(String empresaId, NotaFiscalEntradaItem item) {
        if (item.getEan() != null) {
            var porEan = barcodeRepository.findByEmpresaIdAndCodigoBarrasAndAtivoTrue(empresaId, item.getEan());
            if (porEan.isPresent()) return porEan.map(ProdutoCodigoBarras::getProduto);
        }
        return produtoRepository.findByCodigoInternoAndEmpresaId(
                codigoInterno(item.getNota().getEmitenteCnpj(), item.getCodigoFornecedor()), empresaId);
    }

    private Produto criarProduto(Empresa empresa, NotaFiscalEntrada nota, NotaFiscalEntradaItem item) {
        Produto produto = new Produto();
        produto.setEmpresa(empresa);
        produto.setCodigoInterno(codigoInterno(nota.getEmitenteCnpj(), item.getCodigoFornecedor()));
        produto.setNome(item.getNomeProduto());
        produto.setPreco(item.getPrecoVenda());
        produto.setCategoria(item.getCategoria());
        produto.setUnidadeMedida(item.getUnidadeMedida());
        produto.setStatus(true);
        if (item.getEan() != null) produto.adicionarCodigoBarras(item.getEan(), "EAN", true);
        if (item.getNcm() != null || item.getCest() != null || item.getEan() != null) {
            ProdutoFiscal fiscal = new ProdutoFiscal();
            fiscal.setEmpresa(empresa);
            fiscal.setProduto(produto);
            fiscal.setNcm(item.getNcm());
            fiscal.setCest(item.getCest());
            fiscal.setGtinTributavel(item.getEan());
            fiscal.setUnidadeTributavel(item.getUnidadeComercial());
            produto.setFiscal(fiscal);
        }
        produto = produtoRepository.saveAndFlush(produto);
        auditLogService.record(empresa, "PRODUCT_IMPORTED", "Produto", produto.getIdProduto(), null,
                Map.of("codigoInterno", produto.getCodigoInterno(), "notaFiscalId", nota.getId()), Map.of());
        eventPublisher.publishEvent(new ProdutoCatalogChangedEvent(
                produto.getIdProduto(), ProdutoCatalogChangeReason.PRODUCT_CREATED, Set.of()));
        return produto;
    }

    private void darEntrada(Empresa empresa, Produto produto, NotaFiscalEntradaItem item,
                            NotaFiscalEntrada nota, User usuario, String motivo) {
        EstoqueEmpresa estoque = estoqueRepository.findForUpdate(empresa.getId(), produto.getIdProduto())
                .orElseGet(() -> {
                    EstoqueEmpresa novo = new EstoqueEmpresa();
                    novo.setEmpresa(empresa);
                    novo.setProduto(produto);
                    novo.setQuantidade(BigDecimal.ZERO.setScale(3));
                    novo.setAtivo(true);
                    return estoqueRepository.saveAndFlush(novo);
                });
        BigDecimal anterior = estoque.getQuantidade();
        BigDecimal delta = item.getQuantidadeEntrada();
        BigDecimal posterior = anterior.add(delta).setScale(3, RoundingMode.UNNECESSARY);
        estoque.setQuantidade(posterior);
        estoqueRepository.save(estoque);
        MovimentacaoEstoque movimento = new MovimentacaoEstoque();
        movimento.setEmpresa(empresa);
        movimento.setEstoqueEmpresa(estoque);
        movimento.setTipo(TipoMovimentacaoEstoque.ENTRADA);
        movimento.setQuantidade(delta);
        movimento.setQuantidadeAnterior(anterior);
        movimento.setQuantidadePosterior(posterior);
        movimento.setCreatedBy(usuario);
        movimento.setMotivo(motivo);
        movimento.setChaveIdempotencia("NFE:" + nota.getId() + ":" + item.getId());
        movimentoRepository.save(movimento);
        auditLogService.record(empresa, "ESTOQUE_EMPRESA_ENTRADA", "EstoqueEmpresa", estoque.getId(), null,
                Map.of("quantidade", delta, "saldo", posterior), Map.of("motivo", motivo, "notaFiscalId", nota.getId()));
    }

    private NotaFiscalEntrada nota(String id) {
        return notaRepository.findByIdAndEmpresaId(id, EmpresaContext.require())
                .orElseThrow(() -> new NoSuchElementException("NF-e não encontrada"));
    }

    /** Código estável por fornecedor: a próxima nota dele casa o item mesmo sem EAN. */
    static String codigoInterno(String cnpjEmitente, String codigoFornecedor) {
        String raiz = cnpjEmitente == null ? "" : digitos(cnpjEmitente);
        raiz = raiz.length() > 8 ? raiz.substring(0, 8) : raiz;
        return limitar("NF" + raiz + "-" + codigoFornecedor.trim(), 80);
    }

    static UnidadeMedida unidadeVenda(String unidadeComercial) {
        return switch (unidadeComercial) {
            case "KG", "KGS" -> UnidadeMedida.KG;
            case "L", "LT", "LTS" -> UnidadeMedida.LITRO;
            default -> UnidadeMedida.UN;
        };
    }

    /** Sugestão inicial pela NCM; o administrador pode trocar na revisão. */
    static ProdutoCategoria categoriaPorNcm(String ncm) {
        if (ncm == null || ncm.length() < 4) return ProdutoCategoria.OUTROS;
        String cap = ncm.substring(0, 2);
        String pos = ncm.substring(0, 4);
        if (cap.equals("22")) return ProdutoCategoria.BEBIDA;
        if (Set.of("0401", "0402", "0403", "0404", "0405", "0406").contains(pos)) return ProdutoCategoria.LATICINIOS;
        if (pos.equals("1905")) return ProdutoCategoria.PADARIA;
        if (Set.of("1704", "1806").contains(pos)) return ProdutoCategoria.DOCES;
        if (pos.equals("1902")) return ProdutoCategoria.MASSAS;
        if (Set.of("1006", "0713").contains(pos)) return ProdutoCategoria.GRAOS;
        if (pos.equals("1904")) return ProdutoCategoria.MATINAIS;
        if (Set.of("3402", "3808").contains(pos)) return ProdutoCategoria.LIMPEZA;
        if (Set.of("3401", "3305", "3306", "3307", "4818", "9603").contains(pos)) return ProdutoCategoria.HIGIENE;
        if (cap.equals("02")) return ProdutoCategoria.CARNES;
        if (pos.equals("1601")) return ProdutoCategoria.FRIOS;
        if (pos.equals("0803") || pos.equals("0808")) return ProdutoCategoria.FRUTAS;
        return ProdutoCategoria.OUTROS;
    }

    private static String digitos(String valor) { return valor == null ? "" : valor.replaceAll("\\D", ""); }

    private static String limitar(String valor, int max) {
        return valor.length() <= max ? valor : valor.substring(0, max);
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 indisponível", ex); }
    }

    private User usuarioAtual(String empresaId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user && user.getEmpresa() != null
                && empresaId.equals(user.getEmpresa().getId())) return user;
        return null;
    }
}
