import React, { useEffect } from 'react';
import {
  View,
  Text,
  Alert,
  Button,
  StyleSheet,
  NativeModules,
  NativeEventEmitter,
} from 'react-native';
const { PagSeguro } = NativeModules;

const App = () => {
  const [msg, setMsg] = React.useState('');

  useEffect(() => {
    const emitter = new NativeEventEmitter();
    emitter.addListener('eventPayments', reminder => {
      console.log(reminder);
      setMsg(reminder);
    });
    return () => {
      emitter.removeAllListeners('eventPayments');
    };
  }, []);

  function handlePagSeguro() {
    PagSeguro.setAppIdendification();
    PagSeguro.getSerialNumber()
      .then(res => {
        Alert.alert('Serial', res, [
          {
            text: 'Fechar',
            onPress: () => console.log('Cancel Pressed'),
            style: 'cancel',
          },
        ]);
      })
      .catch(error => {
        Alert.alert('Erro', error, [
          {
            text: 'Fechar',
            onPress: () => console.log('Cancel Pressed'),
            style: 'cancel',
          },
        ]);
      });
  }

  function handlePagSeguroPayment(type) {
    PagSeguro.setAppIdendification();
    setMsg('Aguarde...');
    const payment = {
      amount: 50 * 100,
      installmentType: PagSeguro.INSTALLMENT_TYPE_A_VISTA,
      installments: 1,
      type: type,
      userReference: '123',
      printReceipt: true,
    };
    PagSeguro.doPayment(JSON.stringify(payment))
      .then(res => {
        const { message } = res;
        setMsg(message);
      })
      .catch(error => {
        console.log(error);
      });
  }

  return (
    <View style={style.view}>
      <Text style={style.text}>{msg}</Text>
      <Text />
      <Button
        onPress={() => handlePagSeguro()}
        title="Pag Seguro Serial Number"
        color="#841584"
        accessibilityLabel="Pag Seguro Serial Number"
      />
      <Text />
      <Button
        onPress={() => handlePagSeguroPayment(PagSeguro.PAYMENT_DEBITO)}
        title="Pag Seguro Débito"
        color="#4A148C"
        accessibilityLabel="Pag Seguro Débito"
      />
      <Text />
      <Button
        onPress={() => handlePagSeguroPayment(PagSeguro.PAYMENT_CREDITO)}
        title="Pag Seguro Crédito"
        color="#0D47A1"
        accessibilityLabel="Pag Seguro Crédito"
      />
      <Text />
      <Button
        onPress={() => handlePagSeguroPayment(PagSeguro.PAYMENT_PIX)}
        title="Pag Seguro Pix"
        color="#004D40"
        accessibilityLabel="Pag Seguro Pix"
      />
    </View>
  );
};

const style = StyleSheet.create({
  view: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
export default App;
