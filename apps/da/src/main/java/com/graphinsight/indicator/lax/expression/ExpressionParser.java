// Generated from java-escape by ANTLR 4.11.1

// 定义package
package com.graphinsight.indicator.lax.expression;

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class ExpressionParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.11.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		BR_OPEN=1, BR_CLOSE=2, PLUS=3, MINUS=4, TIMES=5, DIVIDE=6, PERCENT=7, 
		POINT=8, PERCENT_NUMBER=9, NUMBER=10, DIGIT=11, SPACE=12;
	public static final int
		RULE_calc = 0, RULE_expr = 1;
	private static String[] makeRuleNames() {
		return new String[] {
			"calc", "expr"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'('", "')'", "'+'", "'-'", "'*'", "'/'", "'%'", "'.'", null, null, 
			null, "' '"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "BR_OPEN", "BR_CLOSE", "PLUS", "MINUS", "TIMES", "DIVIDE", "PERCENT", 
			"POINT", "PERCENT_NUMBER", "NUMBER", "DIGIT", "SPACE"
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

	public ExpressionParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CalcContext extends ParserRuleContext {
		public CalcContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_calc; }
	 
		public CalcContext() { }
		public void copyFrom(CalcContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CalculationBlockContext extends CalcContext {
		public TerminalNode EOF() { return getToken(ExpressionParser.EOF, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public CalculationBlockContext(CalcContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionListener ) ((ExpressionListener)listener).enterCalculationBlock(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionListener ) ((ExpressionListener)listener).exitCalculationBlock(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExpressionVisitor ) return ((ExpressionVisitor<? extends T>)visitor).visitCalculationBlock(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CalcContext calc() throws RecognitionException {
		CalcContext _localctx = new CalcContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_calc);
		int _la;
		try {
			_localctx = new CalculationBlockContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(7);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (((_la) & ~0x3f) == 0 && ((1L << _la) & 1562L) != 0) {
				{
				{
				setState(4);
				expr(0);
				}
				}
				setState(9);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(10);
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
	public static class ExpressionWithBrContext extends ExprContext {
		public TerminalNode BR_OPEN() { return getToken(ExpressionParser.BR_OPEN, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode BR_CLOSE() { return getToken(ExpressionParser.BR_CLOSE, 0); }
		public ExpressionWithBrContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionListener ) ((ExpressionListener)listener).enterExpressionWithBr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionListener ) ((ExpressionListener)listener).exitExpressionWithBr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExpressionVisitor ) return ((ExpressionVisitor<? extends T>)visitor).visitExpressionWithBr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionTimesOrDivideContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode TIMES() { return getToken(ExpressionParser.TIMES, 0); }
		public TerminalNode DIVIDE() { return getToken(ExpressionParser.DIVIDE, 0); }
		public ExpressionTimesOrDivideContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionListener ) ((ExpressionListener)listener).enterExpressionTimesOrDivide(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionListener ) ((ExpressionListener)listener).exitExpressionTimesOrDivide(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExpressionVisitor ) return ((ExpressionVisitor<? extends T>)visitor).visitExpressionTimesOrDivide(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionNumericContext extends ExprContext {
		public Token sign;
		public Token num;
		public TerminalNode NUMBER() { return getToken(ExpressionParser.NUMBER, 0); }
		public TerminalNode PERCENT_NUMBER() { return getToken(ExpressionParser.PERCENT_NUMBER, 0); }
		public TerminalNode PLUS() { return getToken(ExpressionParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(ExpressionParser.MINUS, 0); }
		public ExpressionNumericContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionListener ) ((ExpressionListener)listener).enterExpressionNumeric(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionListener ) ((ExpressionListener)listener).exitExpressionNumeric(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExpressionVisitor ) return ((ExpressionVisitor<? extends T>)visitor).visitExpressionNumeric(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionPlusOrMinusContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode PLUS() { return getToken(ExpressionParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(ExpressionParser.MINUS, 0); }
		public ExpressionPlusOrMinusContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionListener ) ((ExpressionListener)listener).enterExpressionPlusOrMinus(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExpressionListener ) ((ExpressionListener)listener).exitExpressionPlusOrMinus(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExpressionVisitor ) return ((ExpressionVisitor<? extends T>)visitor).visitExpressionPlusOrMinus(this);
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
		int _startState = 2;
		enterRecursionRule(_localctx, 2, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(21);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BR_OPEN:
				{
				_localctx = new ExpressionWithBrContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(13);
				match(BR_OPEN);
				setState(14);
				expr(0);
				setState(15);
				match(BR_CLOSE);
				}
				break;
			case PLUS:
			case MINUS:
			case PERCENT_NUMBER:
			case NUMBER:
				{
				_localctx = new ExpressionNumericContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(18);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==PLUS || _la==MINUS) {
					{
					setState(17);
					((ExpressionNumericContext)_localctx).sign = _input.LT(1);
					_la = _input.LA(1);
					if ( !(_la==PLUS || _la==MINUS) ) {
						((ExpressionNumericContext)_localctx).sign = (Token)_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					}
				}

				setState(20);
				((ExpressionNumericContext)_localctx).num = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==PERCENT_NUMBER || _la==NUMBER) ) {
					((ExpressionNumericContext)_localctx).num = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(31);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
			while ( _alt!=2 && _alt!= ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(29);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
					case 1:
						{
						_localctx = new ExpressionTimesOrDivideContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(23);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(24);
						((ExpressionTimesOrDivideContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==TIMES || _la==DIVIDE) ) {
							((ExpressionTimesOrDivideContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(25);
						expr(3);
						}
						break;
					case 2:
						{
						_localctx = new ExpressionPlusOrMinusContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(26);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(27);
						((ExpressionPlusOrMinusContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==PLUS || _la==MINUS) ) {
							((ExpressionPlusOrMinusContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(28);
						expr(2);
						}
						break;
					}
					} 
				}
				setState(33);
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

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 1:
			return expr_sempred((ExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expr_sempred(ExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 2);
		case 1:
			return precpred(_ctx, 1);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001\f#\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0001"+
		"\u0000\u0005\u0000\u0006\b\u0000\n\u0000\f\u0000\t\t\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0003\u0001\u0013\b\u0001\u0001\u0001\u0003\u0001\u0016\b"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0005\u0001\u001e\b\u0001\n\u0001\f\u0001!\t\u0001\u0001\u0001"+
		"\u0000\u0001\u0002\u0002\u0000\u0002\u0000\u0003\u0001\u0000\u0003\u0004"+
		"\u0001\u0000\t\n\u0001\u0000\u0005\u0006%\u0000\u0007\u0001\u0000\u0000"+
		"\u0000\u0002\u0015\u0001\u0000\u0000\u0000\u0004\u0006\u0003\u0002\u0001"+
		"\u0000\u0005\u0004\u0001\u0000\u0000\u0000\u0006\t\u0001\u0000\u0000\u0000"+
		"\u0007\u0005\u0001\u0000\u0000\u0000\u0007\b\u0001\u0000\u0000\u0000\b"+
		"\n\u0001\u0000\u0000\u0000\t\u0007\u0001\u0000\u0000\u0000\n\u000b\u0005"+
		"\u0000\u0000\u0001\u000b\u0001\u0001\u0000\u0000\u0000\f\r\u0006\u0001"+
		"\uffff\uffff\u0000\r\u000e\u0005\u0001\u0000\u0000\u000e\u000f\u0003\u0002"+
		"\u0001\u0000\u000f\u0010\u0005\u0002\u0000\u0000\u0010\u0016\u0001\u0000"+
		"\u0000\u0000\u0011\u0013\u0007\u0000\u0000\u0000\u0012\u0011\u0001\u0000"+
		"\u0000\u0000\u0012\u0013\u0001\u0000\u0000\u0000\u0013\u0014\u0001\u0000"+
		"\u0000\u0000\u0014\u0016\u0007\u0001\u0000\u0000\u0015\f\u0001\u0000\u0000"+
		"\u0000\u0015\u0012\u0001\u0000\u0000\u0000\u0016\u001f\u0001\u0000\u0000"+
		"\u0000\u0017\u0018\n\u0002\u0000\u0000\u0018\u0019\u0007\u0002\u0000\u0000"+
		"\u0019\u001e\u0003\u0002\u0001\u0003\u001a\u001b\n\u0001\u0000\u0000\u001b"+
		"\u001c\u0007\u0000\u0000\u0000\u001c\u001e\u0003\u0002\u0001\u0002\u001d"+
		"\u0017\u0001\u0000\u0000\u0000\u001d\u001a\u0001\u0000\u0000\u0000\u001e"+
		"!\u0001\u0000\u0000\u0000\u001f\u001d\u0001\u0000\u0000\u0000\u001f \u0001"+
		"\u0000\u0000\u0000 \u0003\u0001\u0000\u0000\u0000!\u001f\u0001\u0000\u0000"+
		"\u0000\u0005\u0007\u0012\u0015\u001d\u001f";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}