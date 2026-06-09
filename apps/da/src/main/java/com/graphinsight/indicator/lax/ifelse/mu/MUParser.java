// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.lax.ifelse.mu;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class MUParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, OR=2, AND=3, EQ=4, NEQ=5, GT=6, LT=7, GTEQ=8, LTEQ=9, PLUS=10, 
		MINUS=11, MULT=12, DIV=13, MOD=14, POW=15, NOT=16, SCOL=17, ASSIGN=18, 
		OPAR=19, CPAR=20, OBRACE=21, CBRACE=22, TRUE=23, FALSE=24, NIL=25, IF=26, 
		ELSE=27, ID=28, INT=29, FLOAT=30, STRING=31, COMMENT=32, SPACE=33, OTHER=34;
	public static final int
		RULE_parse = 0, RULE_block = 1, RULE_stat = 2, RULE_if_stat = 3, RULE_condition_block = 4, 
		RULE_stat_block = 5, RULE_expr = 6, RULE_atom = 7;
	private static String[] makeRuleNames() {
		return new String[] {
			"parse", "block", "stat", "if_stat", "condition_block", "stat_block", 
			"expr", "atom"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "','", "'||'", "'&&'", "'=='", "'!='", "'>'", "'<'", "'>='", "'<='", 
			"'+'", "'-'", "'*'", "'/'", "'%'", "'^'", "'!'", "';'", "'='", "'('", 
			"')'", "'{'", "'}'", "'true'", "'false'", "'nil'", "'if'", "'else'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, "OR", "AND", "EQ", "NEQ", "GT", "LT", "GTEQ", "LTEQ", "PLUS", 
			"MINUS", "MULT", "DIV", "MOD", "POW", "NOT", "SCOL", "ASSIGN", "OPAR", 
			"CPAR", "OBRACE", "CBRACE", "TRUE", "FALSE", "NIL", "IF", "ELSE", "ID", 
			"INT", "FLOAT", "STRING", "COMMENT", "SPACE", "OTHER"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "java-escape"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public MUParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParseContext extends ParserRuleContext {
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode EOF() { return getToken(MUParser.EOF, 0); }
		public ParseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parse; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterParse(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitParse(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitParse(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParseContext parse() throws RecognitionException {
		ParseContext _localctx = new ParseContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_parse);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(16);
			block();
			setState(17);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BlockContext extends ParserRuleContext {
		public List<StatContext> stat() {
			return getRuleContexts(StatContext.class);
		}
		public StatContext stat(int i) {
			return getRuleContext(StatContext.class,i);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterBlock(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitBlock(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitBlock(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(22);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==IF || _la==OTHER) {
				{
				{
				setState(19);
				stat();
				}
				}
				setState(24);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatContext extends ParserRuleContext {
		public Token OTHER;
		public If_statContext if_stat() {
			return getRuleContext(If_statContext.class,0);
		}
		public TerminalNode OTHER() { return getToken(MUParser.OTHER, 0); }
		public StatContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stat; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterStat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitStat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitStat(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatContext stat() throws RecognitionException {
		StatContext _localctx = new StatContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_stat);
		try {
			setState(28);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IF:
				enterOuterAlt(_localctx, 1);
				{
				setState(25);
				if_stat();
				}
				break;
			case OTHER:
				enterOuterAlt(_localctx, 2);
				{
				setState(26);
				((StatContext)_localctx).OTHER = match(OTHER);
				System.err.println("unknown char: " + (((StatContext)_localctx).OTHER!=null?((StatContext)_localctx).OTHER.getText():null));
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class If_statContext extends ParserRuleContext {
		public TerminalNode IF() { return getToken(MUParser.IF, 0); }
		public TerminalNode OPAR() { return getToken(MUParser.OPAR, 0); }
		public Condition_blockContext condition_block() {
			return getRuleContext(Condition_blockContext.class,0);
		}
		public List<Stat_blockContext> stat_block() {
			return getRuleContexts(Stat_blockContext.class);
		}
		public Stat_blockContext stat_block(int i) {
			return getRuleContext(Stat_blockContext.class,i);
		}
		public TerminalNode CPAR() { return getToken(MUParser.CPAR, 0); }
		public If_statContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_if_stat; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterIf_stat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitIf_stat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitIf_stat(this);
			else return visitor.visitChildren(this);
		}
	}

	public final If_statContext if_stat() throws RecognitionException {
		If_statContext _localctx = new If_statContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_if_stat);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(30);
			match(IF);
			setState(31);
			match(OPAR);
			setState(32);
			condition_block();
			setState(33);
			match(T__0);
			setState(34);
			stat_block();
			setState(35);
			match(T__0);
			setState(36);
			stat_block();
			setState(37);
			match(CPAR);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Condition_blockContext extends ParserRuleContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Condition_blockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_condition_block; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterCondition_block(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitCondition_block(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitCondition_block(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Condition_blockContext condition_block() throws RecognitionException {
		Condition_blockContext _localctx = new Condition_blockContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_condition_block);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(39);
			expr(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Stat_blockContext extends ParserRuleContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Stat_blockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stat_block; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterStat_block(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitStat_block(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitStat_block(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Stat_blockContext stat_block() throws RecognitionException {
		Stat_blockContext _localctx = new Stat_blockContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_stat_block);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(41);
			expr(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExprContext extends ParserRuleContext {
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
	 
		public ExprContext() { }
		public void copyFrom(ExprContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NotExprContext extends ExprContext {
		public TerminalNode NOT() { return getToken(MUParser.NOT, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public NotExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterNotExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitNotExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitNotExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class UnaryMinusExprContext extends ExprContext {
		public TerminalNode MINUS() { return getToken(MUParser.MINUS, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public UnaryMinusExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterUnaryMinusExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitUnaryMinusExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitUnaryMinusExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MultiplicationExprContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode MULT() { return getToken(MUParser.MULT, 0); }
		public TerminalNode DIV() { return getToken(MUParser.DIV, 0); }
		public TerminalNode MOD() { return getToken(MUParser.MOD, 0); }
		public MultiplicationExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterMultiplicationExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitMultiplicationExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitMultiplicationExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AtomExprContext extends ExprContext {
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public AtomExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterAtomExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitAtomExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitAtomExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class OrExprContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode OR() { return getToken(MUParser.OR, 0); }
		public OrExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterOrExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitOrExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitOrExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AdditiveExprContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode PLUS() { return getToken(MUParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(MUParser.MINUS, 0); }
		public AdditiveExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterAdditiveExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitAdditiveExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitAdditiveExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PowExprContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode POW() { return getToken(MUParser.POW, 0); }
		public PowExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterPowExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitPowExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitPowExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class RelationalExprContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode LTEQ() { return getToken(MUParser.LTEQ, 0); }
		public TerminalNode GTEQ() { return getToken(MUParser.GTEQ, 0); }
		public TerminalNode LT() { return getToken(MUParser.LT, 0); }
		public TerminalNode GT() { return getToken(MUParser.GT, 0); }
		public RelationalExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterRelationalExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitRelationalExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitRelationalExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class EqualityExprContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode EQ() { return getToken(MUParser.EQ, 0); }
		public TerminalNode NEQ() { return getToken(MUParser.NEQ, 0); }
		public EqualityExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterEqualityExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitEqualityExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitEqualityExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AndExprContext extends ExprContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode AND() { return getToken(MUParser.AND, 0); }
		public AndExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterAndExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitAndExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitAndExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprContext expr() throws RecognitionException {
		return expr(0);
	}

	private ExprContext expr(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExprContext _localctx = new ExprContext(_ctx, _parentState);
		ExprContext _prevctx = _localctx;
		int _startState = 12;
		enterRecursionRule(_localctx, 12, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(49);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case MINUS:
				{
				_localctx = new UnaryMinusExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(44);
				match(MINUS);
				setState(45);
				expr(9);
				}
				break;
			case NOT:
				{
				_localctx = new NotExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(46);
				match(NOT);
				setState(47);
				expr(8);
				}
				break;
			case OPAR:
			case TRUE:
			case FALSE:
			case NIL:
			case ID:
			case INT:
			case FLOAT:
			case STRING:
				{
				_localctx = new AtomExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(48);
				atom();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(74);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
			while ( _alt!=2 && _alt!= ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(72);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
					case 1:
						{
						_localctx = new PowExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(51);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(52);
						match(POW);
						setState(53);
						expr(11);
						}
						break;
					case 2:
						{
						_localctx = new MultiplicationExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(54);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(55);
						((MultiplicationExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(((_la) & ~0x3f) == 0 && ((1L << _la) & 28672L) != 0) ) {
							((MultiplicationExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(56);
						expr(8);
						}
						break;
					case 3:
						{
						_localctx = new AdditiveExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(57);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(58);
						((AdditiveExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==PLUS || _la==MINUS) ) {
							((AdditiveExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(59);
						expr(7);
						}
						break;
					case 4:
						{
						_localctx = new RelationalExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(60);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(61);
						((RelationalExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(((_la) & ~0x3f) == 0 && ((1L << _la) & 960L) != 0) ) {
							((RelationalExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(62);
						expr(6);
						}
						break;
					case 5:
						{
						_localctx = new EqualityExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(63);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(64);
						((EqualityExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==EQ || _la==NEQ) ) {
							((EqualityExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(65);
						expr(5);
						}
						break;
					case 6:
						{
						_localctx = new AndExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(66);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(67);
						match(AND);
						setState(68);
						expr(4);
						}
						break;
					case 7:
						{
						_localctx = new OrExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(69);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(70);
						match(OR);
						setState(71);
						expr(3);
						}
						break;
					}
					} 
				}
				setState(76);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AtomContext extends ParserRuleContext {
		public AtomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_atom; }
	 
		public AtomContext() { }
		public void copyFrom(AtomContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ParExprContext extends AtomContext {
		public TerminalNode OPAR() { return getToken(MUParser.OPAR, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode CPAR() { return getToken(MUParser.CPAR, 0); }
		public ParExprContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterParExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitParExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitParExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanAtomContext extends AtomContext {
		public TerminalNode TRUE() { return getToken(MUParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(MUParser.FALSE, 0); }
		public BooleanAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterBooleanAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitBooleanAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitBooleanAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IdAtomContext extends AtomContext {
		public TerminalNode ID() { return getToken(MUParser.ID, 0); }
		public IdAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterIdAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitIdAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitIdAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class StringAtomContext extends AtomContext {
		public TerminalNode STRING() { return getToken(MUParser.STRING, 0); }
		public StringAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterStringAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitStringAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitStringAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NilAtomContext extends AtomContext {
		public TerminalNode NIL() { return getToken(MUParser.NIL, 0); }
		public NilAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterNilAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitNilAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitNilAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NumberAtomContext extends AtomContext {
		public TerminalNode INT() { return getToken(MUParser.INT, 0); }
		public TerminalNode FLOAT() { return getToken(MUParser.FLOAT, 0); }
		public NumberAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).enterNumberAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MUListener ) ((MUListener)listener).exitNumberAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MUVisitor ) return ((MUVisitor<? extends T>)visitor).visitNumberAtom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_atom);
		int _la;
		try {
			setState(86);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OPAR:
				_localctx = new ParExprContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(77);
				match(OPAR);
				setState(78);
				expr(0);
				setState(79);
				match(CPAR);
				}
				break;
			case INT:
			case FLOAT:
				_localctx = new NumberAtomContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(81);
				_la = _input.LA(1);
				if ( !(_la==INT || _la==FLOAT) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case TRUE:
			case FALSE:
				_localctx = new BooleanAtomContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(82);
				_la = _input.LA(1);
				if ( !(_la==TRUE || _la==FALSE) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case ID:
				_localctx = new IdAtomContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(83);
				match(ID);
				}
				break;
			case STRING:
				_localctx = new StringAtomContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(84);
				match(STRING);
				}
				break;
			case NIL:
				_localctx = new NilAtomContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(85);
				match(NIL);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 6:
			return expr_sempred((ExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expr_sempred(ExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 10);
		case 1:
			return precpred(_ctx, 7);
		case 2:
			return precpred(_ctx, 6);
		case 3:
			return precpred(_ctx, 5);
		case 4:
			return precpred(_ctx, 4);
		case 5:
			return precpred(_ctx, 3);
		case 6:
			return precpred(_ctx, 2);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001\"Y\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0001"+
		"\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0005\u0001\u0015\b\u0001\n"+
		"\u0001\f\u0001\u0018\t\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0003"+
		"\u0002\u001d\b\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001"+
		"\u0004\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0003\u00062\b\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0005\u0006I\b\u0006\n\u0006\f\u0006L\t\u0006\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0003\u0007W\b\u0007\u0001\u0007\u0000"+
		"\u0001\f\b\u0000\u0002\u0004\u0006\b\n\f\u000e\u0000\u0006\u0001\u0000"+
		"\f\u000e\u0001\u0000\n\u000b\u0001\u0000\u0006\t\u0001\u0000\u0004\u0005"+
		"\u0001\u0000\u001d\u001e\u0001\u0000\u0017\u0018`\u0000\u0010\u0001\u0000"+
		"\u0000\u0000\u0002\u0016\u0001\u0000\u0000\u0000\u0004\u001c\u0001\u0000"+
		"\u0000\u0000\u0006\u001e\u0001\u0000\u0000\u0000\b\'\u0001\u0000\u0000"+
		"\u0000\n)\u0001\u0000\u0000\u0000\f1\u0001\u0000\u0000\u0000\u000eV\u0001"+
		"\u0000\u0000\u0000\u0010\u0011\u0003\u0002\u0001\u0000\u0011\u0012\u0005"+
		"\u0000\u0000\u0001\u0012\u0001\u0001\u0000\u0000\u0000\u0013\u0015\u0003"+
		"\u0004\u0002\u0000\u0014\u0013\u0001\u0000\u0000\u0000\u0015\u0018\u0001"+
		"\u0000\u0000\u0000\u0016\u0014\u0001\u0000\u0000\u0000\u0016\u0017\u0001"+
		"\u0000\u0000\u0000\u0017\u0003\u0001\u0000\u0000\u0000\u0018\u0016\u0001"+
		"\u0000\u0000\u0000\u0019\u001d\u0003\u0006\u0003\u0000\u001a\u001b\u0005"+
		"\"\u0000\u0000\u001b\u001d\u0006\u0002\uffff\uffff\u0000\u001c\u0019\u0001"+
		"\u0000\u0000\u0000\u001c\u001a\u0001\u0000\u0000\u0000\u001d\u0005\u0001"+
		"\u0000\u0000\u0000\u001e\u001f\u0005\u001a\u0000\u0000\u001f \u0005\u0013"+
		"\u0000\u0000 !\u0003\b\u0004\u0000!\"\u0005\u0001\u0000\u0000\"#\u0003"+
		"\n\u0005\u0000#$\u0005\u0001\u0000\u0000$%\u0003\n\u0005\u0000%&\u0005"+
		"\u0014\u0000\u0000&\u0007\u0001\u0000\u0000\u0000\'(\u0003\f\u0006\u0000"+
		"(\t\u0001\u0000\u0000\u0000)*\u0003\f\u0006\u0000*\u000b\u0001\u0000\u0000"+
		"\u0000+,\u0006\u0006\uffff\uffff\u0000,-\u0005\u000b\u0000\u0000-2\u0003"+
		"\f\u0006\t./\u0005\u0010\u0000\u0000/2\u0003\f\u0006\b02\u0003\u000e\u0007"+
		"\u00001+\u0001\u0000\u0000\u00001.\u0001\u0000\u0000\u000010\u0001\u0000"+
		"\u0000\u00002J\u0001\u0000\u0000\u000034\n\n\u0000\u000045\u0005\u000f"+
		"\u0000\u00005I\u0003\f\u0006\u000b67\n\u0007\u0000\u000078\u0007\u0000"+
		"\u0000\u00008I\u0003\f\u0006\b9:\n\u0006\u0000\u0000:;\u0007\u0001\u0000"+
		"\u0000;I\u0003\f\u0006\u0007<=\n\u0005\u0000\u0000=>\u0007\u0002\u0000"+
		"\u0000>I\u0003\f\u0006\u0006?@\n\u0004\u0000\u0000@A\u0007\u0003\u0000"+
		"\u0000AI\u0003\f\u0006\u0005BC\n\u0003\u0000\u0000CD\u0005\u0003\u0000"+
		"\u0000DI\u0003\f\u0006\u0004EF\n\u0002\u0000\u0000FG\u0005\u0002\u0000"+
		"\u0000GI\u0003\f\u0006\u0003H3\u0001\u0000\u0000\u0000H6\u0001\u0000\u0000"+
		"\u0000H9\u0001\u0000\u0000\u0000H<\u0001\u0000\u0000\u0000H?\u0001\u0000"+
		"\u0000\u0000HB\u0001\u0000\u0000\u0000HE\u0001\u0000\u0000\u0000IL\u0001"+
		"\u0000\u0000\u0000JH\u0001\u0000\u0000\u0000JK\u0001\u0000\u0000\u0000"+
		"K\r\u0001\u0000\u0000\u0000LJ\u0001\u0000\u0000\u0000MN\u0005\u0013\u0000"+
		"\u0000NO\u0003\f\u0006\u0000OP\u0005\u0014\u0000\u0000PW\u0001\u0000\u0000"+
		"\u0000QW\u0007\u0004\u0000\u0000RW\u0007\u0005\u0000\u0000SW\u0005\u001c"+
		"\u0000\u0000TW\u0005\u001f\u0000\u0000UW\u0005\u0019\u0000\u0000VM\u0001"+
		"\u0000\u0000\u0000VQ\u0001\u0000\u0000\u0000VR\u0001\u0000\u0000\u0000"+
		"VS\u0001\u0000\u0000\u0000VT\u0001\u0000\u0000\u0000VU\u0001\u0000\u0000"+
		"\u0000W\u000f\u0001\u0000\u0000\u0000\u0006\u0016\u001c1HJV";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}