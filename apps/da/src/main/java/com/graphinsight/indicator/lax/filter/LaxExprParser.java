// Generated from /Users/xiaojiwei/indicator/indicator/src/main/java/com/graphinsight/indicator/lax/filter/LaxExpr.g4 by ANTLR 4.13.1
package com.graphinsight.indicator.lax.filter;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class LaxExprParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, FILTER=8, MEA=9, 
		DIM=10, IF=11, FUNNAME=12, OR=13, AND=14, EQ=15, NEQ=16, GT=17, LT=18, 
		GTEQ=19, LTEQ=20, IN=21, BETWEEN=22, TRUE=23, FALSE=24, NIL=25, MUL=26, 
		DIV=27, ADD=28, SUB=29, ID=30, INT=31, FLOAT=32, STRING=33, NEWLINE=34, 
		WS=35;
	public static final int
		RULE_prog = 0, RULE_stat = 1, RULE_if_stat = 2, RULE_condition_block = 3, 
		RULE_stat_block = 4, RULE_expr = 5, RULE_atom = 6, RULE_exprs = 7, RULE_fun = 8, 
		RULE_meas = 9, RULE_dims = 10, RULE_filters = 11, RULE_filter = 12, RULE_value = 13;
	private static String[] makeRuleNames() {
		return new String[] {
			"prog", "stat", "if_stat", "condition_block", "stat_block", "expr", "atom", 
			"exprs", "fun", "meas", "dims", "filters", "filter", "value"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'('", "','", "')'", "'fixed'", "'exclude'", "':('", "'filters:('", 
			null, null, null, "'if'", null, "'||'", "'&&'", "'=='", "'!='", "'>'", 
			"'<'", "'>='", "'<='", "'in'", "'between'", "'true'", "'false'", "'nil'", 
			"'*'", "'/'", "'+'", "'-'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, "FILTER", "MEA", "DIM", 
			"IF", "FUNNAME", "OR", "AND", "EQ", "NEQ", "GT", "LT", "GTEQ", "LTEQ", 
			"IN", "BETWEEN", "TRUE", "FALSE", "NIL", "MUL", "DIV", "ADD", "SUB", 
			"ID", "INT", "FLOAT", "STRING", "NEWLINE", "WS"
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
	public String getGrammarFileName() { return "LaxExpr.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public LaxExprParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProgContext extends ParserRuleContext {
		public List<StatContext> stat() {
			return getRuleContexts(StatContext.class);
		}
		public StatContext stat(int i) {
			return getRuleContext(StatContext.class,i);
		}
		public ProgContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_prog; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterProg(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitProg(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitProg(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ProgContext prog() throws RecognitionException {
		ProgContext _localctx = new ProgContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_prog);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(29); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(28);
				stat();
				}
				}
				setState(31); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 16164854786L) != 0) );
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
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public StatContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stat; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterStat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitStat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitStat(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatContext stat() throws RecognitionException {
		StatContext _localctx = new StatContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_stat);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(33);
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
	public static class If_statContext extends ParserRuleContext {
		public TerminalNode IF() { return getToken(LaxExprParser.IF, 0); }
		public Condition_blockContext condition_block() {
			return getRuleContext(Condition_blockContext.class,0);
		}
		public List<Stat_blockContext> stat_block() {
			return getRuleContexts(Stat_blockContext.class);
		}
		public Stat_blockContext stat_block(int i) {
			return getRuleContext(Stat_blockContext.class,i);
		}
		public If_statContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_if_stat; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterIf_stat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitIf_stat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitIf_stat(this);
			else return visitor.visitChildren(this);
		}
	}

	public final If_statContext if_stat() throws RecognitionException {
		If_statContext _localctx = new If_statContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_if_stat);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(35);
			match(IF);
			setState(36);
			match(T__0);
			setState(37);
			condition_block();
			setState(38);
			match(T__1);
			setState(39);
			stat_block();
			setState(40);
			match(T__1);
			setState(41);
			stat_block();
			setState(42);
			match(T__2);
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
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterCondition_block(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitCondition_block(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitCondition_block(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Condition_blockContext condition_block() throws RecognitionException {
		Condition_blockContext _localctx = new Condition_blockContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_condition_block);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(44);
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
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterStat_block(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitStat_block(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitStat_block(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Stat_blockContext stat_block() throws RecognitionException {
		Stat_blockContext _localctx = new Stat_blockContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_stat_block);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(46);
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
	public static class IfStatContext extends ExprContext {
		public If_statContext if_stat() {
			return getRuleContext(If_statContext.class,0);
		}
		public IfStatContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterIfStat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitIfStat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitIfStat(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ParensContext extends ExprContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public ParensContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterParens(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitParens(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitParens(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FunsContext extends ExprContext {
		public FunContext fun() {
			return getRuleContext(FunContext.class,0);
		}
		public FunsContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterFuns(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitFuns(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitFuns(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class StringContext extends ExprContext {
		public TerminalNode STRING() { return getToken(LaxExprParser.STRING, 0); }
		public StringContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterString(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitString(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitString(this);
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
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterAtomExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitAtomExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitAtomExpr(this);
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
		public TerminalNode OR() { return getToken(LaxExprParser.OR, 0); }
		public OrExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterOrExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitOrExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitOrExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AddSubContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode ADD() { return getToken(LaxExprParser.ADD, 0); }
		public TerminalNode SUB() { return getToken(LaxExprParser.SUB, 0); }
		public AddSubContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterAddSub(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitAddSub(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitAddSub(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FloatContext extends ExprContext {
		public TerminalNode FLOAT() { return getToken(LaxExprParser.FLOAT, 0); }
		public FloatContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterFloat(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitFloat(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitFloat(this);
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
		public TerminalNode LTEQ() { return getToken(LaxExprParser.LTEQ, 0); }
		public TerminalNode GTEQ() { return getToken(LaxExprParser.GTEQ, 0); }
		public TerminalNode LT() { return getToken(LaxExprParser.LT, 0); }
		public TerminalNode GT() { return getToken(LaxExprParser.GT, 0); }
		public RelationalExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterRelationalExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitRelationalExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitRelationalExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MulDivContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode MUL() { return getToken(LaxExprParser.MUL, 0); }
		public TerminalNode DIV() { return getToken(LaxExprParser.DIV, 0); }
		public MulDivContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterMulDiv(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitMulDiv(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitMulDiv(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExprListContext extends ExprContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public ExprsContext exprs() {
			return getRuleContext(ExprsContext.class,0);
		}
		public ExprListContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterExprList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitExprList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitExprList(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DimzContext extends ExprContext {
		public DimsContext dims() {
			return getRuleContext(DimsContext.class,0);
		}
		public DimzContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterDimz(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitDimz(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitDimz(this);
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
		public TerminalNode EQ() { return getToken(LaxExprParser.EQ, 0); }
		public TerminalNode NEQ() { return getToken(LaxExprParser.NEQ, 0); }
		public EqualityExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterEqualityExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitEqualityExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitEqualityExpr(this);
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
		public TerminalNode AND() { return getToken(LaxExprParser.AND, 0); }
		public AndExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterAndExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitAndExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitAndExpr(this);
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
		int _startState = 10;
		enterRecursionRule(_localctx, 10, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(59);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				{
				_localctx = new IfStatContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(49);
				if_stat();
				}
				break;
			case 2:
				{
				_localctx = new AtomExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(50);
				atom();
				}
				break;
			case 3:
				{
				_localctx = new FunsContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(51);
				fun();
				}
				break;
			case 4:
				{
				_localctx = new ParensContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(52);
				match(T__0);
				setState(53);
				expr(0);
				setState(54);
				match(T__2);
				}
				break;
			case 5:
				{
				_localctx = new FloatContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(56);
				match(FLOAT);
				}
				break;
			case 6:
				{
				_localctx = new StringContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(57);
				match(STRING);
				}
				break;
			case 7:
				{
				_localctx = new DimzContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(58);
				dims();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(86);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,3,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(84);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
					case 1:
						{
						_localctx = new MulDivContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(61);
						if (!(precpred(_ctx, 14))) throw new FailedPredicateException(this, "precpred(_ctx, 14)");
						setState(62);
						((MulDivContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==MUL || _la==DIV) ) {
							((MulDivContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(63);
						expr(15);
						}
						break;
					case 2:
						{
						_localctx = new AddSubContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(64);
						if (!(precpred(_ctx, 13))) throw new FailedPredicateException(this, "precpred(_ctx, 13)");
						setState(65);
						((AddSubContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==ADD || _la==SUB) ) {
							((AddSubContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(66);
						expr(14);
						}
						break;
					case 3:
						{
						_localctx = new RelationalExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(67);
						if (!(precpred(_ctx, 12))) throw new FailedPredicateException(this, "precpred(_ctx, 12)");
						setState(68);
						((RelationalExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 1966080L) != 0)) ) {
							((RelationalExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(69);
						expr(13);
						}
						break;
					case 4:
						{
						_localctx = new EqualityExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(70);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(71);
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
						setState(72);
						expr(12);
						}
						break;
					case 5:
						{
						_localctx = new AndExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(73);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(74);
						match(AND);
						setState(75);
						expr(11);
						}
						break;
					case 6:
						{
						_localctx = new OrExprContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(76);
						if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
						setState(77);
						match(OR);
						setState(78);
						expr(10);
						}
						break;
					case 7:
						{
						_localctx = new ExprListContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(79);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(80);
						match(T__0);
						setState(81);
						exprs();
						setState(82);
						match(T__2);
						}
						break;
					}
					} 
				}
				setState(88);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,3,_ctx);
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
	public static class BooleanAtomContext extends AtomContext {
		public TerminalNode TRUE() { return getToken(LaxExprParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(LaxExprParser.FALSE, 0); }
		public BooleanAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterBooleanAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitBooleanAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitBooleanAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IdAtomContext extends AtomContext {
		public TerminalNode ID() { return getToken(LaxExprParser.ID, 0); }
		public IdAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterIdAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitIdAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitIdAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NilAtomContext extends AtomContext {
		public TerminalNode NIL() { return getToken(LaxExprParser.NIL, 0); }
		public NilAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterNilAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitNilAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitNilAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NumberAtomContext extends AtomContext {
		public TerminalNode INT() { return getToken(LaxExprParser.INT, 0); }
		public TerminalNode FLOAT() { return getToken(LaxExprParser.FLOAT, 0); }
		public NumberAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterNumberAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitNumberAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitNumberAtom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_atom);
		int _la;
		try {
			setState(93);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INT:
			case FLOAT:
				_localctx = new NumberAtomContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(89);
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
				enterOuterAlt(_localctx, 2);
				{
				setState(90);
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
				enterOuterAlt(_localctx, 3);
				{
				setState(91);
				match(ID);
				}
				break;
			case NIL:
				_localctx = new NilAtomContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(92);
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

	@SuppressWarnings("CheckReturnValue")
	public static class ExprsContext extends ParserRuleContext {
		public ExprsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exprs; }
	 
		public ExprsContext() { }
		public void copyFrom(ExprsContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpsContext extends ExprsContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public ExpsContext(ExprsContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterExps(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitExps(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitExps(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprsContext exprs() throws RecognitionException {
		ExprsContext _localctx = new ExprsContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_exprs);
		int _la;
		try {
			_localctx = new ExpsContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(95);
			expr(0);
			setState(100);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__1) {
				{
				{
				setState(96);
				match(T__1);
				setState(97);
				expr(0);
				}
				}
				setState(102);
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
	public static class FunContext extends ParserRuleContext {
		public FunContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fun; }
	 
		public FunContext() { }
		public void copyFrom(FunContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FunctionContext extends FunContext {
		public TerminalNode FUNNAME() { return getToken(LaxExprParser.FUNNAME, 0); }
		public MeasContext meas() {
			return getRuleContext(MeasContext.class,0);
		}
		public ExprsContext exprs() {
			return getRuleContext(ExprsContext.class,0);
		}
		public DimsContext dims() {
			return getRuleContext(DimsContext.class,0);
		}
		public FunctionContext(FunContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitFunction(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitFunction(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunContext fun() throws RecognitionException {
		FunContext _localctx = new FunContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_fun);
		try {
			_localctx = new FunctionContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(103);
			match(FUNNAME);
			setState(104);
			match(T__0);
			setState(109);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
			case 1:
				{
				setState(105);
				meas();
				}
				break;
			case 2:
				{
				setState(106);
				exprs();
				}
				break;
			case 3:
				{
				setState(107);
				dims();
				}
				break;
			case 4:
				{
				}
				break;
			}
			setState(111);
			match(T__2);
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
	public static class MeasContext extends ParserRuleContext {
		public MeasContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_meas; }
	 
		public MeasContext() { }
		public void copyFrom(MeasContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MeaContext extends MeasContext {
		public Token scope;
		public TerminalNode MEA() { return getToken(LaxExprParser.MEA, 0); }
		public List<DimsContext> dims() {
			return getRuleContexts(DimsContext.class);
		}
		public DimsContext dims(int i) {
			return getRuleContext(DimsContext.class,i);
		}
		public List<FiltersContext> filters() {
			return getRuleContexts(FiltersContext.class);
		}
		public FiltersContext filters(int i) {
			return getRuleContext(FiltersContext.class,i);
		}
		public MeaContext(MeasContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterMea(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitMea(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitMea(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MeasContext meas() throws RecognitionException {
		MeasContext _localctx = new MeasContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_meas);
		int _la;
		try {
			int _alt;
			_localctx = new MeaContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(113);
			match(MEA);
			setState(122);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(114);
					match(T__1);
					setState(115);
					((MeaContext)_localctx).scope = _input.LT(1);
					_la = _input.LA(1);
					if ( !(_la==T__3 || _la==T__4) ) {
						((MeaContext)_localctx).scope = (Token)_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(116);
					match(T__5);
					setState(117);
					dims();
					setState(118);
					match(T__2);
					}
					} 
				}
				setState(124);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
			}
			setState(132);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__1) {
				{
				{
				setState(125);
				match(T__1);
				setState(126);
				match(T__6);
				setState(127);
				filters();
				setState(128);
				match(T__2);
				}
				}
				setState(134);
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
	public static class DimsContext extends ParserRuleContext {
		public DimsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dims; }
	 
		public DimsContext() { }
		public void copyFrom(DimsContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DimContext extends DimsContext {
		public List<TerminalNode> DIM() { return getTokens(LaxExprParser.DIM); }
		public TerminalNode DIM(int i) {
			return getToken(LaxExprParser.DIM, i);
		}
		public DimContext(DimsContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterDim(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitDim(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitDim(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DimsContext dims() throws RecognitionException {
		DimsContext _localctx = new DimsContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_dims);
		try {
			int _alt;
			_localctx = new DimContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(135);
			match(DIM);
			setState(140);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,9,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(136);
					match(T__1);
					setState(137);
					match(DIM);
					}
					} 
				}
				setState(142);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,9,_ctx);
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
	public static class FiltersContext extends ParserRuleContext {
		public FiltersContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_filters; }
	 
		public FiltersContext() { }
		public void copyFrom(FiltersContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FilsContext extends FiltersContext {
		public List<FilterContext> filter() {
			return getRuleContexts(FilterContext.class);
		}
		public FilterContext filter(int i) {
			return getRuleContext(FilterContext.class,i);
		}
		public FilsContext(FiltersContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterFils(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitFils(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitFils(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FiltersContext filters() throws RecognitionException {
		FiltersContext _localctx = new FiltersContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_filters);
		int _la;
		try {
			_localctx = new FilsContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(143);
			filter();
			setState(148);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__1) {
				{
				{
				setState(144);
				match(T__1);
				setState(145);
				filter();
				}
				}
				setState(150);
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
	public static class FilterContext extends ParserRuleContext {
		public FilterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_filter; }
	 
		public FilterContext() { }
		public void copyFrom(FilterContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FilContext extends FilterContext {
		public Token ops;
		public DimsContext dims() {
			return getRuleContext(DimsContext.class,0);
		}
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public TerminalNode GTEQ() { return getToken(LaxExprParser.GTEQ, 0); }
		public TerminalNode LTEQ() { return getToken(LaxExprParser.LTEQ, 0); }
		public TerminalNode IN() { return getToken(LaxExprParser.IN, 0); }
		public TerminalNode BETWEEN() { return getToken(LaxExprParser.BETWEEN, 0); }
		public FilContext(FilterContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterFil(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitFil(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitFil(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FilterContext filter() throws RecognitionException {
		FilterContext _localctx = new FilterContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_filter);
		int _la;
		try {
			_localctx = new FilContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(151);
			dims();
			setState(152);
			((FilContext)_localctx).ops = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 7864320L) != 0)) ) {
				((FilContext)_localctx).ops = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(153);
			value();
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
	public static class ValueContext extends ParserRuleContext {
		public ValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_value; }
	 
		public ValueContext() { }
		public void copyFrom(ValueContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class VsContext extends ValueContext {
		public TerminalNode STRING() { return getToken(LaxExprParser.STRING, 0); }
		public VsContext(ValueContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).enterVs(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LaxExprListener ) ((LaxExprListener)listener).exitVs(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LaxExprVisitor ) return ((LaxExprVisitor<? extends T>)visitor).visitVs(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ValueContext value() throws RecognitionException {
		ValueContext _localctx = new ValueContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_value);
		try {
			_localctx = new VsContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(155);
			match(STRING);
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
		case 5:
			return expr_sempred((ExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expr_sempred(ExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 14);
		case 1:
			return precpred(_ctx, 13);
		case 2:
			return precpred(_ctx, 12);
		case 3:
			return precpred(_ctx, 11);
		case 4:
			return precpred(_ctx, 10);
		case 5:
			return precpred(_ctx, 9);
		case 6:
			return precpred(_ctx, 5);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001#\u009e\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0001\u0000\u0004\u0000\u001e\b\u0000\u000b"+
		"\u0000\f\u0000\u001f\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0003\u0005<\b\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0005\u0005U\b"+
		"\u0005\n\u0005\f\u0005X\t\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0003\u0006^\b\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0005"+
		"\u0007c\b\u0007\n\u0007\f\u0007f\t\u0007\u0001\b\u0001\b\u0001\b\u0001"+
		"\b\u0001\b\u0001\b\u0003\bn\b\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0001\t\u0005\ty\b\t\n\t\f\t|\t\t\u0001\t\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0005\t\u0083\b\t\n\t\f\t\u0086\t\t\u0001\n"+
		"\u0001\n\u0001\n\u0005\n\u008b\b\n\n\n\f\n\u008e\t\n\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0005\u000b\u0093\b\u000b\n\u000b\f\u000b\u0096\t\u000b"+
		"\u0001\f\u0001\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0000\u0001\n"+
		"\u000e\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018"+
		"\u001a\u0000\b\u0001\u0000\u001a\u001b\u0001\u0000\u001c\u001d\u0001\u0000"+
		"\u0011\u0014\u0001\u0000\u000f\u0010\u0001\u0000\u001f \u0001\u0000\u0017"+
		"\u0018\u0001\u0000\u0004\u0005\u0001\u0000\u0013\u0016\u00a8\u0000\u001d"+
		"\u0001\u0000\u0000\u0000\u0002!\u0001\u0000\u0000\u0000\u0004#\u0001\u0000"+
		"\u0000\u0000\u0006,\u0001\u0000\u0000\u0000\b.\u0001\u0000\u0000\u0000"+
		"\n;\u0001\u0000\u0000\u0000\f]\u0001\u0000\u0000\u0000\u000e_\u0001\u0000"+
		"\u0000\u0000\u0010g\u0001\u0000\u0000\u0000\u0012q\u0001\u0000\u0000\u0000"+
		"\u0014\u0087\u0001\u0000\u0000\u0000\u0016\u008f\u0001\u0000\u0000\u0000"+
		"\u0018\u0097\u0001\u0000\u0000\u0000\u001a\u009b\u0001\u0000\u0000\u0000"+
		"\u001c\u001e\u0003\u0002\u0001\u0000\u001d\u001c\u0001\u0000\u0000\u0000"+
		"\u001e\u001f\u0001\u0000\u0000\u0000\u001f\u001d\u0001\u0000\u0000\u0000"+
		"\u001f \u0001\u0000\u0000\u0000 \u0001\u0001\u0000\u0000\u0000!\"\u0003"+
		"\n\u0005\u0000\"\u0003\u0001\u0000\u0000\u0000#$\u0005\u000b\u0000\u0000"+
		"$%\u0005\u0001\u0000\u0000%&\u0003\u0006\u0003\u0000&\'\u0005\u0002\u0000"+
		"\u0000\'(\u0003\b\u0004\u0000()\u0005\u0002\u0000\u0000)*\u0003\b\u0004"+
		"\u0000*+\u0005\u0003\u0000\u0000+\u0005\u0001\u0000\u0000\u0000,-\u0003"+
		"\n\u0005\u0000-\u0007\u0001\u0000\u0000\u0000./\u0003\n\u0005\u0000/\t"+
		"\u0001\u0000\u0000\u000001\u0006\u0005\uffff\uffff\u00001<\u0003\u0004"+
		"\u0002\u00002<\u0003\f\u0006\u00003<\u0003\u0010\b\u000045\u0005\u0001"+
		"\u0000\u000056\u0003\n\u0005\u000067\u0005\u0003\u0000\u00007<\u0001\u0000"+
		"\u0000\u00008<\u0005 \u0000\u00009<\u0005!\u0000\u0000:<\u0003\u0014\n"+
		"\u0000;0\u0001\u0000\u0000\u0000;2\u0001\u0000\u0000\u0000;3\u0001\u0000"+
		"\u0000\u0000;4\u0001\u0000\u0000\u0000;8\u0001\u0000\u0000\u0000;9\u0001"+
		"\u0000\u0000\u0000;:\u0001\u0000\u0000\u0000<V\u0001\u0000\u0000\u0000"+
		"=>\n\u000e\u0000\u0000>?\u0007\u0000\u0000\u0000?U\u0003\n\u0005\u000f"+
		"@A\n\r\u0000\u0000AB\u0007\u0001\u0000\u0000BU\u0003\n\u0005\u000eCD\n"+
		"\f\u0000\u0000DE\u0007\u0002\u0000\u0000EU\u0003\n\u0005\rFG\n\u000b\u0000"+
		"\u0000GH\u0007\u0003\u0000\u0000HU\u0003\n\u0005\fIJ\n\n\u0000\u0000J"+
		"K\u0005\u000e\u0000\u0000KU\u0003\n\u0005\u000bLM\n\t\u0000\u0000MN\u0005"+
		"\r\u0000\u0000NU\u0003\n\u0005\nOP\n\u0005\u0000\u0000PQ\u0005\u0001\u0000"+
		"\u0000QR\u0003\u000e\u0007\u0000RS\u0005\u0003\u0000\u0000SU\u0001\u0000"+
		"\u0000\u0000T=\u0001\u0000\u0000\u0000T@\u0001\u0000\u0000\u0000TC\u0001"+
		"\u0000\u0000\u0000TF\u0001\u0000\u0000\u0000TI\u0001\u0000\u0000\u0000"+
		"TL\u0001\u0000\u0000\u0000TO\u0001\u0000\u0000\u0000UX\u0001\u0000\u0000"+
		"\u0000VT\u0001\u0000\u0000\u0000VW\u0001\u0000\u0000\u0000W\u000b\u0001"+
		"\u0000\u0000\u0000XV\u0001\u0000\u0000\u0000Y^\u0007\u0004\u0000\u0000"+
		"Z^\u0007\u0005\u0000\u0000[^\u0005\u001e\u0000\u0000\\^\u0005\u0019\u0000"+
		"\u0000]Y\u0001\u0000\u0000\u0000]Z\u0001\u0000\u0000\u0000][\u0001\u0000"+
		"\u0000\u0000]\\\u0001\u0000\u0000\u0000^\r\u0001\u0000\u0000\u0000_d\u0003"+
		"\n\u0005\u0000`a\u0005\u0002\u0000\u0000ac\u0003\n\u0005\u0000b`\u0001"+
		"\u0000\u0000\u0000cf\u0001\u0000\u0000\u0000db\u0001\u0000\u0000\u0000"+
		"de\u0001\u0000\u0000\u0000e\u000f\u0001\u0000\u0000\u0000fd\u0001\u0000"+
		"\u0000\u0000gh\u0005\f\u0000\u0000hm\u0005\u0001\u0000\u0000in\u0003\u0012"+
		"\t\u0000jn\u0003\u000e\u0007\u0000kn\u0003\u0014\n\u0000ln\u0006\b\uffff"+
		"\uffff\u0000mi\u0001\u0000\u0000\u0000mj\u0001\u0000\u0000\u0000mk\u0001"+
		"\u0000\u0000\u0000ml\u0001\u0000\u0000\u0000no\u0001\u0000\u0000\u0000"+
		"op\u0005\u0003\u0000\u0000p\u0011\u0001\u0000\u0000\u0000qz\u0005\t\u0000"+
		"\u0000rs\u0005\u0002\u0000\u0000st\u0007\u0006\u0000\u0000tu\u0005\u0006"+
		"\u0000\u0000uv\u0003\u0014\n\u0000vw\u0005\u0003\u0000\u0000wy\u0001\u0000"+
		"\u0000\u0000xr\u0001\u0000\u0000\u0000y|\u0001\u0000\u0000\u0000zx\u0001"+
		"\u0000\u0000\u0000z{\u0001\u0000\u0000\u0000{\u0084\u0001\u0000\u0000"+
		"\u0000|z\u0001\u0000\u0000\u0000}~\u0005\u0002\u0000\u0000~\u007f\u0005"+
		"\u0007\u0000\u0000\u007f\u0080\u0003\u0016\u000b\u0000\u0080\u0081\u0005"+
		"\u0003\u0000\u0000\u0081\u0083\u0001\u0000\u0000\u0000\u0082}\u0001\u0000"+
		"\u0000\u0000\u0083\u0086\u0001\u0000\u0000\u0000\u0084\u0082\u0001\u0000"+
		"\u0000\u0000\u0084\u0085\u0001\u0000\u0000\u0000\u0085\u0013\u0001\u0000"+
		"\u0000\u0000\u0086\u0084\u0001\u0000\u0000\u0000\u0087\u008c\u0005\n\u0000"+
		"\u0000\u0088\u0089\u0005\u0002\u0000\u0000\u0089\u008b\u0005\n\u0000\u0000"+
		"\u008a\u0088\u0001\u0000\u0000\u0000\u008b\u008e\u0001\u0000\u0000\u0000"+
		"\u008c\u008a\u0001\u0000\u0000\u0000\u008c\u008d\u0001\u0000\u0000\u0000"+
		"\u008d\u0015\u0001\u0000\u0000\u0000\u008e\u008c\u0001\u0000\u0000\u0000"+
		"\u008f\u0094\u0003\u0018\f\u0000\u0090\u0091\u0005\u0002\u0000\u0000\u0091"+
		"\u0093\u0003\u0018\f\u0000\u0092\u0090\u0001\u0000\u0000\u0000\u0093\u0096"+
		"\u0001\u0000\u0000\u0000\u0094\u0092\u0001\u0000\u0000\u0000\u0094\u0095"+
		"\u0001\u0000\u0000\u0000\u0095\u0017\u0001\u0000\u0000\u0000\u0096\u0094"+
		"\u0001\u0000\u0000\u0000\u0097\u0098\u0003\u0014\n\u0000\u0098\u0099\u0007"+
		"\u0007\u0000\u0000\u0099\u009a\u0003\u001a\r\u0000\u009a\u0019\u0001\u0000"+
		"\u0000\u0000\u009b\u009c\u0005!\u0000\u0000\u009c\u001b\u0001\u0000\u0000"+
		"\u0000\u000b\u001f;TV]dmz\u0084\u008c\u0094";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}