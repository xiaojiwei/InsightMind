package com.graphinsight.indicator;

import java.util.*;


public class Calculator {

    public static void main(String[] args) {
        //定义逆波兰表达式(3+4)*5-6
        //将中缀表达式转为  list 再操作
        //将对应的中缀表达式的值转换为后缀表达式对应的 list
        String expression = "(1220*2-1)+(2*3)*5-6";
        List<String> InfixExpression = tolist(expression);
        System.out.println("中缀表达式=" + InfixExpression);
        List<String> parseSuffixExpressionList = parseSuffixExpressionList(InfixExpression);
        System.out.println("后缀表达式=" + parseSuffixExpressionList);
        System.out.printf("表达式的值为%d", calculate(parseSuffixExpressionList));
        System.out.println();
    }

    public static List<String> tolist(String s) {
        List<String> ls = new ArrayList<String>();
        int i = 0;
        String str;
        char c;
        do {

            if ((c = s.charAt(i)) < 48 || (c = s.charAt(i)) > 57) {
                ls.add("" + c);
                i++;
            } else {
                str = "";
                while (i < s.length() && ((c = s.charAt(i)) >= 48) && ((c = s.charAt(i)) <= 57)) {
                    str += c;
                    i++;
                }
                ls.add(str);
            }
        } while (i < s.length());
        return ls;
    }

    public static List<String> parseSuffixExpressionList(List<String> ls) {

        Stack<String> stack = new Stack<String>();//

        List<String> list = new ArrayList<String>();
        for (String item : ls) {
            if (item.matches("\\d+")) {
                list.add(item);

            } else if (item.equals("(")) {
                stack.push(item);

            } else if (item.equals(")")) {

                while (!stack.peek().equals("(")) {//stack.peek
                    list.add(stack.pop());
                }
                stack.pop();
            } else {
                while (stack.size() != 0 && getValue(stack.peek()) >= getValue(item)) {
                    list.add(stack.pop());
                }

                stack.push(item);
            }
        }
        while (stack.size() != 0) {
            list.add(stack.pop());
        }
        return list;
    }

    public static List<String> getliststring(String suffixExpression) {
        String[] split = suffixExpression.split(" ");
        List<String> list = new ArrayList<String>();
        for (String ele : split) {
            list.add(ele);
        }
        return list;
    }

    class Token {

    }

    public static int calculate(List<String> ls) {

        Stack<String> stack = new Stack<String>();
        for (String item : ls) {

            if (item.matches("\\d+")) {//
                stack.push(item);
            } else {

                int num2 = Integer.parseInt(stack.pop());
                int num1 = Integer.parseInt(stack.pop());
                int res;
                if (item.equals("+")) {
                    res = num1 + num2;
                } else if (item.equals("-")) {
                    res = num1 - num2;
                } else if (item.equals("*")) {
                    res = num1 * num2;
                } else if (item.equals("/")) {
                    res = num1 / num2;
                } else {
                    throw new RuntimeException("运算符未知");
                }
                stack.push(res + "");
            }
        }
        return Integer.parseInt(stack.pop());
    }

    public static int getValue(String op) {
        int result = 0;
        switch (op) {
            case "+":
                result = 1;
                break;
            case "-":
                result = 1;
                break;
            case "*":
                result = 2;
                break;
            case "/":
                result = 2;
                break;
        }
        return result;
    }

}