// Generated from java-escape by ANTLR 4.11.1
package com.graphinsight.indicator.xlax.lod;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class LodExprParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, FILTER=13, MEA=14, DIM=15, MUL=16, DIV=17, 
		ADD=18, SUB=19, ID=20, INT=21, STRING=22, NEWLINE=23, WS=24;
	public static final int
		RULE_prog = 0, RULE_stat = 1, RULE_expr = 2, RULE_meas = 3, RULE_dims = 4, 
		RULE_filters = 5, RULE_filter = 6, RULE_value = 7;
	private static String[] makeRuleNames() {
		return new String[] {
			"prog", "stat", "expr", "meas", "dims", "filters", "filter", "value"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'('", "')'", "'Calculate('", "','", "'fixed'", "'exclude'", "':('", 
			"'filters:('", "'>='", "'<='", "'in'", "'between'", null, null, null, 
			"'*'", "'/'", "'+'", "'-'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, "FILTER", "MEA", "DIM", "MUL", "DIV", "ADD", "SUB", "ID", "INT", 
			"STRING", "NEWLINE", "WS"
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

	public LodExprParser(TokenStream input) {
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
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterProg(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitProg(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitProg(this);
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
			setState(17); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(16);
				stat();
				}
				}
				setState(19); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==T__0 || _la==T__2 );
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
		public StatContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stat; }
	 
		public StatContext() { }
		public void copyFrom(StatContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PrintExprContext extends StatContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public PrintExprContext(StatContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterPrintExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitPrintExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitPrintExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatContext stat() throws RecognitionException {
		StatContext _localctx = new StatContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_stat);
		try {
			_localctx = new PrintExprContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(21);
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
	public static class ParensContext extends ExprContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public ParensContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterParens(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitParens(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitParens(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MeasureContext extends ExprContext {
		public MeasContext meas() {
			return getRuleContext(MeasContext.class,0);
		}
		public MeasureContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterMeasure(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitMeasure(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitMeasure(this);
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
		public TerminalNode MUL() { return getToken(LodExprParser.MUL, 0); }
		public TerminalNode DIV() { return getToken(LodExprParser.DIV, 0); }
		public MulDivContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterMulDiv(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitMulDiv(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitMulDiv(this);
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
		public TerminalNode ADD() { return getToken(LodExprParser.ADD, 0); }
		public TerminalNode SUB() { return getToken(LodExprParser.SUB, 0); }
		public AddSubContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterAddSub(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitAddSub(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitAddSub(this);
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
		int _startState = 4;
		enterRecursionRule(_localctx, 4, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(29);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
				{
				_localctx = new MeasureContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(24);
				meas();
				}
				break;
			case T__0:
				{
				_localctx = new ParensContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(25);
				match(T__0);
				setState(26);
				expr(0);
				setState(27);
				match(T__1);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(39);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,3,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(37);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
					case 1:
						{
						_localctx = new MulDivContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(31);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(32);
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
						setState(33);
						expr(5);
						}
						break;
					case 2:
						{
						_localctx = new AddSubContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(34);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(35);
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
						setState(36);
						expr(4);
						}
						break;
					}
					} 
				}
				setState(41);
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
		public TerminalNode MEA() { return getToken(LodExprParser.MEA, 0); }
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
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterMea(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitMea(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitMea(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CalcContext extends MeasContext {
		public MeasContext meas() {
			return getRuleContext(MeasContext.class,0);
		}
		public CalcContext(MeasContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterCalc(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitCalc(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitCalc(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MeasContext meas() throws RecognitionException {
		MeasContext _localctx = new MeasContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_meas);
		int _la;
		try {
			int _alt;
			setState(70);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
			case 1:
				_localctx = new MeaContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(42);
				match(T__2);
				setState(43);
				match(MEA);
				setState(52);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(44);
						match(T__3);
						setState(45);
						((MeaContext)_localctx).scope = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__4 || _la==T__5) ) {
							((MeaContext)_localctx).scope = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(46);
						match(T__6);
						setState(47);
						dims();
						setState(48);
						match(T__1);
						}
						} 
					}
					setState(54);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
				}
				setState(62);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__3) {
					{
					{
					setState(55);
					match(T__3);
					setState(56);
					match(T__7);
					setState(57);
					filters();
					setState(58);
					match(T__1);
					}
					}
					setState(64);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(65);
				match(T__1);
				}
				break;
			case 2:
				_localctx = new CalcContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(66);
				match(T__2);
				setState(67);
				meas();
				setState(68);
				match(T__1);
				}
				break;
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
		public List<TerminalNode> DIM() { return getTokens(LodExprParser.DIM); }
		public TerminalNode DIM(int i) {
			return getToken(LodExprParser.DIM, i);
		}
		public DimContext(DimsContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterDim(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitDim(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitDim(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DimsContext dims() throws RecognitionException {
		DimsContext _localctx = new DimsContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_dims);
		int _la;
		try {
			_localctx = new DimContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(72);
			match(DIM);
			setState(77);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__3) {
				{
				{
				setState(73);
				match(T__3);
				setState(74);
				match(DIM);
				}
				}
				setState(79);
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
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterFils(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitFils(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitFils(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FiltersContext filters() throws RecognitionException {
		FiltersContext _localctx = new FiltersContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_filters);
		int _la;
		try {
			_localctx = new FilsContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(80);
			filter();
			setState(85);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__3) {
				{
				{
				setState(81);
				match(T__3);
				setState(82);
				filter();
				}
				}
				setState(87);
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
		public FilContext(FilterContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterFil(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitFil(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitFil(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FilterContext filter() throws RecognitionException {
		FilterContext _localctx = new FilterContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_filter);
		int _la;
		try {
			_localctx = new FilContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(88);
			match(T__0);
			setState(89);
			dims();
			setState(90);
			((FilContext)_localctx).ops = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((_la) & ~0x3f) == 0 && ((1L << _la) & 7680L) != 0) ) {
				((FilContext)_localctx).ops = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(91);
			value();
			setState(92);
			match(T__1);
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
		public TerminalNode STRING() { return getToken(LodExprParser.STRING, 0); }
		public VsContext(ValueContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).enterVs(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof LodExprListener ) ((LodExprListener)listener).exitVs(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof LodExprVisitor ) return ((LodExprVisitor<? extends T>)visitor).visitVs(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ValueContext value() throws RecognitionException {
		ValueContext _localctx = new ValueContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_value);
		try {
			_localctx = new VsContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(94);
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
		case 2:
			return expr_sempred((ExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expr_sempred(ExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 4);
		case 1:
			return precpred(_ctx, 3);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u0018a\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0001"+
		"\u0000\u0004\u0000\u0012\b\u0000\u000b\u0000\f\u0000\u0013\u0001\u0001"+
		"\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0003\u0002\u001e\b\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0005\u0002&\b\u0002\n\u0002\f\u0002"+
		")\t\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0005\u00033\b\u0003\n\u0003\f\u0003"+
		"6\t\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0005\u0003=\b\u0003\n\u0003\f\u0003@\t\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0003\u0003\u0003G\b\u0003\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0005\u0004L\b\u0004\n\u0004\f\u0004O\t\u0004"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0005\u0005T\b\u0005\n\u0005\f\u0005"+
		"W\t\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0000\u0001\u0004\b\u0000"+
		"\u0002\u0004\u0006\b\n\f\u000e\u0000\u0004\u0001\u0000\u0010\u0011\u0001"+
		"\u0000\u0012\u0013\u0001\u0000\u0005\u0006\u0001\u0000\t\fa\u0000\u0011"+
		"\u0001\u0000\u0000\u0000\u0002\u0015\u0001\u0000\u0000\u0000\u0004\u001d"+
		"\u0001\u0000\u0000\u0000\u0006F\u0001\u0000\u0000\u0000\bH\u0001\u0000"+
		"\u0000\u0000\nP\u0001\u0000\u0000\u0000\fX\u0001\u0000\u0000\u0000\u000e"+
		"^\u0001\u0000\u0000\u0000\u0010\u0012\u0003\u0002\u0001\u0000\u0011\u0010"+
		"\u0001\u0000\u0000\u0000\u0012\u0013\u0001\u0000\u0000\u0000\u0013\u0011"+
		"\u0001\u0000\u0000\u0000\u0013\u0014\u0001\u0000\u0000\u0000\u0014\u0001"+
		"\u0001\u0000\u0000\u0000\u0015\u0016\u0003\u0004\u0002\u0000\u0016\u0003"+
		"\u0001\u0000\u0000\u0000\u0017\u0018\u0006\u0002\uffff\uffff\u0000\u0018"+
		"\u001e\u0003\u0006\u0003\u0000\u0019\u001a\u0005\u0001\u0000\u0000\u001a"+
		"\u001b\u0003\u0004\u0002\u0000\u001b\u001c\u0005\u0002\u0000\u0000\u001c"+
		"\u001e\u0001\u0000\u0000\u0000\u001d\u0017\u0001\u0000\u0000\u0000\u001d"+
		"\u0019\u0001\u0000\u0000\u0000\u001e\'\u0001\u0000\u0000\u0000\u001f "+
		"\n\u0004\u0000\u0000 !\u0007\u0000\u0000\u0000!&\u0003\u0004\u0002\u0005"+
		"\"#\n\u0003\u0000\u0000#$\u0007\u0001\u0000\u0000$&\u0003\u0004\u0002"+
		"\u0004%\u001f\u0001\u0000\u0000\u0000%\"\u0001\u0000\u0000\u0000&)\u0001"+
		"\u0000\u0000\u0000\'%\u0001\u0000\u0000\u0000\'(\u0001\u0000\u0000\u0000"+
		"(\u0005\u0001\u0000\u0000\u0000)\'\u0001\u0000\u0000\u0000*+\u0005\u0003"+
		"\u0000\u0000+4\u0005\u000e\u0000\u0000,-\u0005\u0004\u0000\u0000-.\u0007"+
		"\u0002\u0000\u0000./\u0005\u0007\u0000\u0000/0\u0003\b\u0004\u000001\u0005"+
		"\u0002\u0000\u000013\u0001\u0000\u0000\u00002,\u0001\u0000\u0000\u0000"+
		"36\u0001\u0000\u0000\u000042\u0001\u0000\u0000\u000045\u0001\u0000\u0000"+
		"\u00005>\u0001\u0000\u0000\u000064\u0001\u0000\u0000\u000078\u0005\u0004"+
		"\u0000\u000089\u0005\b\u0000\u00009:\u0003\n\u0005\u0000:;\u0005\u0002"+
		"\u0000\u0000;=\u0001\u0000\u0000\u0000<7\u0001\u0000\u0000\u0000=@\u0001"+
		"\u0000\u0000\u0000><\u0001\u0000\u0000\u0000>?\u0001\u0000\u0000\u0000"+
		"?A\u0001\u0000\u0000\u0000@>\u0001\u0000\u0000\u0000AG\u0005\u0002\u0000"+
		"\u0000BC\u0005\u0003\u0000\u0000CD\u0003\u0006\u0003\u0000DE\u0005\u0002"+
		"\u0000\u0000EG\u0001\u0000\u0000\u0000F*\u0001\u0000\u0000\u0000FB\u0001"+
		"\u0000\u0000\u0000G\u0007\u0001\u0000\u0000\u0000HM\u0005\u000f\u0000"+
		"\u0000IJ\u0005\u0004\u0000\u0000JL\u0005\u000f\u0000\u0000KI\u0001\u0000"+
		"\u0000\u0000LO\u0001\u0000\u0000\u0000MK\u0001\u0000\u0000\u0000MN\u0001"+
		"\u0000\u0000\u0000N\t\u0001\u0000\u0000\u0000OM\u0001\u0000\u0000\u0000"+
		"PU\u0003\f\u0006\u0000QR\u0005\u0004\u0000\u0000RT\u0003\f\u0006\u0000"+
		"SQ\u0001\u0000\u0000\u0000TW\u0001\u0000\u0000\u0000US\u0001\u0000\u0000"+
		"\u0000UV\u0001\u0000\u0000\u0000V\u000b\u0001\u0000\u0000\u0000WU\u0001"+
		"\u0000\u0000\u0000XY\u0005\u0001\u0000\u0000YZ\u0003\b\u0004\u0000Z[\u0007"+
		"\u0003\u0000\u0000[\\\u0003\u000e\u0007\u0000\\]\u0005\u0002\u0000\u0000"+
		"]\r\u0001\u0000\u0000\u0000^_\u0005\u0016\u0000\u0000_\u000f\u0001\u0000"+
		"\u0000\u0000\t\u0013\u001d%\'4>FMU";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}