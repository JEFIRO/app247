package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.*;
import com.jefiro.app247.domain.model.appclient.*;
import com.jefiro.app247.domain.model.appclient.AppClienteConfig.LoyaltyMode;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.PrecoCalculado;
import com.jefiro.app247.domain.model.dto.appclient.AppClienteContracts.*;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.*;
import com.jefiro.app247.infra.service.comprovante.ComprovanteDigitalService;
import com.jefiro.app247.infra.service.comprovante.ComprovanteService;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Service
public class AppClienteService {
    private static final String CUSTOMER_QR_PREFIX="app247://customer-link/";
    private static final Duration QR_TTL=Duration.ofMinutes(2);
    private static final SecureRandom RANDOM=new SecureRandom();
    @Autowired private UserRepository users;
    @Autowired private AppClienteConfigRepository configs;
    @Autowired private EstoqueCondominioRepository stocks;
    @Autowired private PricingService pricing;
    @Autowired private OrderRepository orders;
    @Autowired private LoyaltyAccountRepository accounts;
    @Autowired private LoyaltyMovementRepository movements;
    @Autowired private RewardRepository rewards;
    @Autowired private CustomerCouponRepository coupons;
    @Autowired private RewardRedemptionRepository redemptions;
    @Autowired private AppClientePreferenceRepository preferences;
    @Autowired private PurchaseSupportRepository supports;
    @Autowired private ComprovanteService comprovantes;
    @Autowired private ComprovanteDigitalService comprovantesDigitais;
    @Autowired private StringRedisTemplate redis;
    @Autowired private CustomerIdentificationTokenRepository identificationTokens;
    @Value("${app.customer-link.ttl-seconds:120}") private long customerTokenTtlSeconds;

    @Transactional(readOnly=true)
    public Bootstrap bootstrap(){
        User u=currentUser(); AppClienteConfig c=config(u); Instant now=Instant.now();
        return new Bootstrap(new UserSummary(u.getIdUser(),fullName(u),u.getCpf(),u.getEmail()),
                new CondominioSummary(u.getCondominio().getIdCondominio(),u.getCondominio().getNome()),features(c),c.getLoyaltyMode().name(),now);
    }

    @Transactional(readOnly=true)
    public PageResponse<CatalogItem> catalog(String search, ProdutoCategoria category,int page,int size){
        User u=currentUser(); AppClienteConfig config=config(u); requireFeature(config.isCatalogo(),"CATALOG_DISABLED","Catálogo indisponível");
        String q=search==null?"":search.strip().toLowerCase(Locale.ROOT);
        List<CatalogItem> all=stocks.findCurrentCatalogForTenant(EmpresaContext.require(),u.getCondominio().getIdCondominio()).stream()
                .map(s->s.getProduto()).filter(p->(q.isEmpty()||(p.getNome()!=null&&p.getNome().toLowerCase(Locale.ROOT).contains(q))
                        ||(p.getDescricao()!=null&&p.getDescricao().toLowerCase(Locale.ROOT).contains(q)))
                        &&(category==null||p.getCategoria()==category)).map(p->catalogItem(p,u.getCondominio(),config.isPromocoes())).toList();
        int from=Math.min(page*size,all.size()),to=Math.min(from+size,all.size());
        return new PageResponse<>(all.subList(from,to),page,size,all.size(),(int)Math.ceil(all.size()/(double)size));
    }

    @Transactional(readOnly=true)
    public PageResponse<PurchaseSummary> purchases(int page,int size){
        User u=currentUser(); var result=orders.findOwnedPage(u.getIdUser(),EmpresaContext.require(),
                u.getCondominio().getIdCondominio(),
                PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt")));
        return new PageResponse<>(result.map(this::purchaseSummary).getContent(),page,size,result.getTotalElements(),result.getTotalPages());
    }

    @Transactional(readOnly=true)
    public PurchaseDetail purchase(String id){return detail(ownedOrder(id));}

    @Transactional(readOnly=true)
    public byte[] receipt(String id){
        Order o=ownedOrder(id); requireApproved(o); return renderReceipt(o);
    }

    @Transactional(readOnly=true)
    public ComprovanteDigitalResponse receiptData(String id){
        Order o=ownedOrder(id); requireApproved(o); return comprovantesDigitais.montar(o);
    }

    @Transactional(readOnly=true)
    public Accepted sendReceipt(String id,ReceiptSendRequest request){
        Order o=ownedOrder(id); requireApproved(o);
        comprovantes.enviar(new ComprovanteRequest(o.getTerminal().getIdTerminal(),o.getIdOrder(),request.canal(),request.destinatario()));
        return new Accepted("ACCEPTED");
    }

    @Transactional
    public CustomerIdentificationTokenResponse identificationToken(){
        User u=currentUser(); requireFeature(config(u).isQrIdentificacao(),"CUSTOMER_QR_DISABLED","Identificação por QR indisponível");
        Instant now=Instant.now();
        Duration ttl=Duration.ofSeconds(Math.max(30,customerTokenTtlSeconds));
        identificationTokens.invalidateActiveForUser(u.getIdUser(),now);
        String opaque=randomToken();
        CustomerIdentificationToken token=new CustomerIdentificationToken();
        token.setTokenHash(sha256(opaque));
        token.setUser(u);
        token.setEmpresa(u.getEmpresa());
        token.setCondominio(u.getCondominio());
        token.setExpiresAt(now.plus(ttl));
        identificationTokens.save(token);
        return new CustomerIdentificationTokenResponse(CUSTOMER_QR_PREFIX+opaque,token.getExpiresAt(),now);
    }

    @Transactional(readOnly=true)
    public Loyalty loyalty(){
        User u=currentUser(); AppClienteConfig c=config(u);
        if(!loyaltyEnabled(c))return new Loyalty(false,"NONE",null,null,null,List.of());
        LoyaltyAccount a=accounts.findByUserIdUserAndEmpresaId(u.getIdUser(),EmpresaContext.require()).orElse(null);
        long points=a==null?0:a.getPointsBalance(); BigDecimal cashback=a==null?BigDecimal.ZERO:MoneyPolicy.charged(a.getCashbackBalance());
        List<LoyaltyMovementResponse> history=a==null?List.of():movements.findTop50ByAccountIdOrderByCreatedAtDesc(a.getId()).stream()
                .map(m->new LoyaltyMovementResponse(m.getId(),m.getType(),m.getPointsDelta(),m.getCashbackDelta(),m.getDescription(),m.getCreatedAt())).toList();
        RewardResponse next=c.getLoyaltyMode()==LoyaltyMode.POINTS?rewardResponses(u,points).stream().filter(r->!r.eligible()).findFirst().orElse(null):null;
        return new Loyalty(true,c.getLoyaltyMode().name(),c.getLoyaltyMode()==LoyaltyMode.POINTS?points:null,
                c.getLoyaltyMode()==LoyaltyMode.CASHBACK?cashback:null,next,history);
    }

    @Transactional(readOnly=true)
    public List<RewardResponse> rewardList(){
        User u=currentUser(); AppClienteConfig c=config(u); requireFeature(loyaltyEnabled(c)&&c.isRecompensas()&&c.getLoyaltyMode()==LoyaltyMode.POINTS,"REWARDS_DISABLED","Recompensas indisponíveis");
        long balance=accounts.findByUserIdUserAndEmpresaId(u.getIdUser(),EmpresaContext.require()).map(LoyaltyAccount::getPointsBalance).orElse(0L);
        return rewardResponses(u,balance);
    }

    @Transactional
    public RedemptionResponse redeem(String rewardId,String key){
        if(key==null||key.isBlank()||key.length()>180)throw new ApiBusinessException(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED","Idempotency-Key é obrigatório");
        User u=currentUser(); AppClienteConfig c=config(u); requireFeature(c.isRecompensas()&&c.isPontos()&&c.getLoyaltyMode()==LoyaltyMode.POINTS,"REWARDS_DISABLED","Resgate de pontos indisponível");
        Optional<RewardRedemption> old=redemptions.findByUserIdUserAndIdempotencyKey(u.getIdUser(),key);
        if(old.isPresent())return new RedemptionResponse(old.get().getId(),old.get().getCoupon().getId(),"COMPLETED");
        Reward r=rewards.findByIdAndEmpresaId(rewardId,EmpresaContext.require()).filter(Reward::isAtivo).filter(x->x.getValidade().isAfter(Instant.now()))
                .filter(x->x.getCondominio()==null||x.getCondominio().getIdCondominio().equals(u.getCondominio().getIdCondominio()))
                .orElseThrow(()->new ApiBusinessException(HttpStatus.NOT_FOUND,"REWARD_NOT_FOUND","Recompensa não encontrada"));
        if(redemptions.countByUserIdUserAndRewardId(u.getIdUser(),r.getId())>=r.getLimiteUsoPorUsuario())
            throw new ApiBusinessException(HttpStatus.CONFLICT,"REWARD_ALREADY_IN_USE","Limite de uso desta recompensa atingido");
        LoyaltyAccount a=accounts.findForUpdate(u.getIdUser(),EmpresaContext.require()).orElseThrow(()->new ApiBusinessException(HttpStatus.CONFLICT,"INSUFFICIENT_POINTS","Saldo de pontos insuficiente"));
        old=redemptions.findByUserIdUserAndIdempotencyKey(u.getIdUser(),key);
        if(old.isPresent())return new RedemptionResponse(old.get().getId(),old.get().getCoupon().getId(),"COMPLETED");
        if(a.getPointsBalance()<r.getCustoPontos())throw new ApiBusinessException(HttpStatus.CONFLICT,"INSUFFICIENT_POINTS","Saldo de pontos insuficiente");
        a.setPointsBalance(a.getPointsBalance()-r.getCustoPontos());
        CustomerCoupon coupon=new CustomerCoupon(); coupon.setEmpresa(u.getEmpresa());coupon.setUser(u);coupon.setReward(r);coupon.setNome(r.getNome());
        coupon.setTipo(r.getTipoDesconto());coupon.setValor(r.getValorDesconto());coupon.setValorMinimoCompra(r.getValorMinimoCompra());
        coupon.setDescontoMaximo(r.getDescontoMaximo());coupon.setValidade(Instant.now().plus(Duration.ofDays(r.getValidadeDias())));coupons.save(coupon);
        LoyaltyMovement m=new LoyaltyMovement();m.setEmpresa(u.getEmpresa());m.setAccount(a);m.setType("REDEMPTION");m.setPointsDelta(-r.getCustoPontos());
        m.setDescription("Resgate: "+r.getNome());m.setIdempotencyKey("redeem:"+u.getIdUser()+":"+key);movements.save(m);
        RewardRedemption redemption=new RewardRedemption();redemption.setEmpresa(u.getEmpresa());redemption.setUser(u);redemption.setReward(r);redemption.setCoupon(coupon);
        redemption.setIdempotencyKey(key);redemption.setPointsSpent(r.getCustoPontos());redemptions.save(redemption);
        return new RedemptionResponse(redemption.getId(),coupon.getId(),"COMPLETED");
    }

    @Transactional
    public List<CouponResponse> couponList(){
        User u=currentUser();requireFeature(config(u).isCupons(),"COUPONS_DISABLED","Cupons indisponíveis");Instant now=Instant.now();
        List<CustomerCoupon> list=coupons.findByUserIdUserAndEmpresaIdOrderByCreatedAtDesc(u.getIdUser(),EmpresaContext.require());
        list.stream().filter(c->c.getStatus()==CustomerCoupon.CouponStatus.AVAILABLE&&!c.getValidade().isAfter(now)).forEach(c->c.setStatus(CustomerCoupon.CouponStatus.EXPIRED));
        return list.stream().map(this::couponResponse).toList();
    }

    @Transactional(readOnly=true)
    public TokenResponse couponToken(String id){
        User u=currentUser();requireFeature(config(u).isCupons(),"COUPONS_DISABLED","Cupons indisponíveis");
        CustomerCoupon c=coupons.findByIdAndUserIdUserAndEmpresaId(id,u.getIdUser(),EmpresaContext.require())
                .orElseThrow(()->new ApiBusinessException(HttpStatus.NOT_FOUND,"COUPON_NOT_FOUND","Cupom não encontrado"));
        if(c.getStatus()!=CustomerCoupon.CouponStatus.AVAILABLE||!c.getValidade().isAfter(Instant.now()))throw new ApiBusinessException(HttpStatus.CONFLICT,"COUPON_UNAVAILABLE","Cupom indisponível");
        Instant now=Instant.now();String token=randomToken();redis.opsForValue().set("app:coupon-token:"+token,c.getId()+"|"+EmpresaContext.require(),QR_TTL);
        return new TokenResponse(token,now.plus(QR_TTL),now);
    }

    @Transactional(readOnly=true)
    public PreferenceResponse getPreferences(){
        User u=currentUser();requireFeature(config(u).isPreferencias(),"PREFERENCES_DISABLED","Preferências indisponíveis");
        return preferences.findByUserIdAndEmpresaId(u.getIdUser(),EmpresaContext.require()).map(p->new PreferenceResponse(p.isAutoEmail(),p.isAutoWhatsapp())).orElse(new PreferenceResponse(false,false));
    }

    @Transactional
    public PreferenceResponse putPreferences(PreferenceRequest request){
        User u=currentUser();requireFeature(config(u).isPreferencias(),"PREFERENCES_DISABLED","Preferências indisponíveis");
        AppClientePreference p=preferences.findByUserIdAndEmpresaId(u.getIdUser(),EmpresaContext.require()).orElseGet(()->{AppClientePreference x=new AppClientePreference();x.setUser(u);x.setEmpresa(u.getEmpresa());return x;});
        p.setAutoEmail(request.autoEmail());p.setAutoWhatsapp(request.autoWhatsapp());preferences.save(p);return new PreferenceResponse(p.isAutoEmail(),p.isAutoWhatsapp());
    }

    @Transactional
    public SupportResponse support(String orderId,SupportRequest request){
        User u=currentUser();requireFeature(config(u).isSuporte(),"SUPPORT_DISABLED","Suporte indisponível");Order o=ownedOrder(orderId);
        PurchaseSupport s=new PurchaseSupport();s.setEmpresa(u.getEmpresa());s.setUser(u);s.setOrder(o);s.setCategoria(request.categoria());s.setMensagem(request.mensagem());
        s.setProtocolo("SUP-"+DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(Instant.now())+"-"+randomDigits(6));supports.save(s);
        return new SupportResponse(s.getProtocolo(),s.getStatus());
    }

    private User currentUser(){
        Authentication a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null||!(a.getPrincipal() instanceof User principal))throw new ApiBusinessException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Autenticação obrigatória");
        User u=users.findById(principal.getIdUser())
                .orElseThrow(()->new ApiBusinessException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Usuário autenticado não encontrado"));
        if(u.getEmpresa()==null)throw new ApiBusinessException(HttpStatus.CONFLICT,"TENANT_LINK_REQUIRED","A conta ainda não possui vínculo autorizado com uma empresa");
        if(EmpresaContext.get()==null||!u.getEmpresa().getId().equals(EmpresaContext.get()))throw new ApiBusinessException(HttpStatus.FORBIDDEN,"TENANT_MISMATCH","Empresa autenticada inválida");
        if(u.getCondominio()==null||!Boolean.TRUE.equals(u.getCondominio().getAtivo()))throw new ApiBusinessException(HttpStatus.CONFLICT,"CONDOMINIUM_REQUIRED","Usuário não possui condomínio ativo");
        if(!u.getCondominio().getEmpresa().getId().equals(EmpresaContext.require()))throw new ApiBusinessException(HttpStatus.FORBIDDEN,"TENANT_MISMATCH","Condomínio não pertence à empresa autenticada");
        return u;
    }
    private AppClienteConfig config(User u){return configs.findById(EmpresaContext.require()).orElseGet(()->AppClienteConfig.defaults(u.getEmpresa()));}
    private Order ownedOrder(String id){User u=currentUser();return orders.findOwnedDetail(id,u.getIdUser(),EmpresaContext.require(),u.getCondominio().getIdCondominio()).orElseThrow(()->new ApiBusinessException(HttpStatus.NOT_FOUND,"PURCHASE_NOT_FOUND","Compra não encontrada"));}
    private void requireApproved(Order o){if(o.getStatus()!=OrderStatus.PROCESSED)throw new ApiBusinessException(HttpStatus.CONFLICT,"PURCHASE_NOT_APPROVED","Comprovante disponível somente para compra aprovada");}
    private static void requireFeature(boolean enabled,String code,String msg){if(!enabled)throw new ApiBusinessException(HttpStatus.CONFLICT,code,msg);}
    private CatalogItem catalogItem(Produto p,com.jefiro.app247.domain.model.Condominio c,boolean promotionsEnabled){PrecoCalculado x=pricing.calcular(p,c,Instant.now());BigDecimal finalPrice=promotionsEnabled?x.precoCalculado():x.precoOriginal();return new CatalogItem(p.getIdProduto(),p.getNome(),p.getDescricao(),p.getFoto(),p.getCategoria(),p.getUnidadeMedida().name(),MoneyPolicy.charged(x.precoOriginal()),MoneyPolicy.charged(finalPrice),!promotionsEnabled||x.promocao()==null?null:new Promotion(x.promocao().getIdPromocao(),x.promocao().getNome(),x.promocao().getTipo().name(),x.promocao().getValor()));}
    private PurchaseSummary purchaseSummary(Order o){return new PurchaseSummary(o.getIdOrder(),o.getStatus().name(),MoneyPolicy.charged(o.getSubtotal()),MoneyPolicy.charged(o.getDesconto()),MoneyPolicy.charged(o.getTotalCobrado()),o.getCreatedAt(),o.getPaidAt());}
    private PurchaseDetail detail(Order o){return new PurchaseDetail(o.getIdOrder(),o.getStatus().name(),MoneyPolicy.charged(o.getSubtotal()),MoneyPolicy.charged(o.getDesconto()),MoneyPolicy.charged(o.getTotalCobrado()),o.getCreatedAt(),o.getPaidAt(),o.getItems().stream().map(i->new PurchaseItem(i.getId(),i.getProduto().getIdProduto(),i.getCodigoInterno(),i.getCodigoBarras(),i.getNome(),i.getUnidadeMedida().name(),i.getQuantidade(),MoneyPolicy.charged(i.getPrecoOriginal()),MoneyPolicy.charged(i.getPrecoUnitarioAplicado()),i.getTipoPromocao()==null?null:i.getTipoPromocao().name(),i.getValorPromocao(),MoneyPolicy.charged(i.getDescontoCalculado()),MoneyPolicy.charged(i.getSubtotalCalculado()))).toList());}
    private List<RewardResponse> rewardResponses(User u,long balance){Instant now=Instant.now();return rewards.findAvailableForClient(EmpresaContext.require(),u.getCondominio().getIdCondominio(),now).stream().map(r->{long used=redemptions.countByUserIdUserAndRewardId(u.getIdUser(),r.getId());boolean limitReached=used>=r.getLimiteUsoPorUsuario();boolean enough=balance>=r.getCustoPontos();return new RewardResponse(r.getId(),r.getNome(),r.getTipoDesconto().name(),MoneyPolicy.charged(r.getValorDesconto()),r.getCustoPontos(),MoneyPolicy.charged(r.getValorMinimoCompra()),r.getDescontoMaximo()==null?null:MoneyPolicy.charged(r.getDescontoMaximo()),r.getValidade(),enough&&!limitReached,limitReached?"REDEMPTION_LIMIT_REACHED":enough?null:"INSUFFICIENT_POINTS");}).toList();}
    private CouponResponse couponResponse(CustomerCoupon c){return new CouponResponse(c.getId(),c.getNome(),c.getTipo().name(),MoneyPolicy.charged(c.getValor()),MoneyPolicy.charged(c.getValorMinimoCompra()),c.getDescontoMaximo()==null?null:MoneyPolicy.charged(c.getDescontoMaximo()),c.getValidade(),c.getStatus().name());}
    private static Features features(AppClienteConfig c){boolean loyalty=loyaltyEnabled(c);boolean points=loyalty&&c.getLoyaltyMode()==LoyaltyMode.POINTS;boolean cashback=loyalty&&c.getLoyaltyMode()==LoyaltyMode.CASHBACK;return new Features(c.isCatalogo(),c.isCatalogo()&&c.isPromocoes(),loyalty,points,cashback,points&&c.isRecompensas(),points&&c.isCupons(),c.isQrIdentificacao(),c.isPreferencias(),c.isSuporte());}
    private static boolean loyaltyEnabled(AppClienteConfig c){return c.isFidelidade()&&c.getLoyaltyMode()!=LoyaltyMode.NONE&&((c.getLoyaltyMode()==LoyaltyMode.POINTS&&c.isPontos())||(c.getLoyaltyMode()==LoyaltyMode.CASHBACK&&c.isCashback()));}
    private static String fullName(User u){return u.getSobrenome()==null||u.getSobrenome().isBlank()?u.getNome():u.getNome()+" "+u.getSobrenome();}
    private static String randomToken(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String sha256(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 indisponível",impossible);}
    }
    private static String randomDigits(int n){StringBuilder s=new StringBuilder();for(int i=0;i<n;i++)s.append(RANDOM.nextInt(10));return s.toString();}
    private static byte[] renderReceipt(Order o){try{int h=260+o.getItems().size()*28;BufferedImage image=new BufferedImage(760,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,760,h);g.setColor(Color.BLACK);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,24));g.drawString("APP 24/7 — COMPROVANTE",32,42);g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,15));g.drawString("Pedido: "+o.getIdOrder(),32,78);g.drawString("Data: "+o.getCreatedAt(),32,102);int y=140;for(OrderItem i:o.getItems()){g.drawString(i.getNome()+"  x"+i.getQuantidade()+"  R$ "+MoneyPolicy.charged(i.getSubtotalCalculado()),32,y);y+=28;}g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));g.drawString("TOTAL: R$ "+MoneyPolicy.charged(o.getTotalCobrado()),32,y+25);g.dispose();ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException("Não foi possível gerar o comprovante",e);}}
}
