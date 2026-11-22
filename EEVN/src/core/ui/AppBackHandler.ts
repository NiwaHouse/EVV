import { useEffect } from 'react';
import { BackHandler } from 'react-native';

/**
 * 画面およびダイアログ状態に応じて Android システムの戻るボタンを制御するフック。
 *
 * すべてのハンドラ処理を `try-catch` で保護し、例外による強制終了を防止する。
 *
 * [責任]: 画面ごとのシステムバック操作ルーティング。
 * [影響する状態]: 検索バー・ボトムシート閉鎖、フォルダ履歴復帰、画面遷移。
 * [潜在的エラー]: BackHandler 登録/解除時の例外はログ記録して無視する。
 */
export function useAppBackHandler(onBackPress: () => boolean): void {
  useEffect(() => {
    try {
      const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
        try {
          return onBackPress();
        } catch (e) {
          console.warn('BackHandler callback error:', e);
          return false;
        }
      });
      return () => {
        try {
          subscription.remove();
        } catch (e) {
          console.warn('BackHandler remove error:', e);
        }
      };
    } catch (e) {
      console.warn('BackHandler addEventListener error:', e);
      return undefined;
    }
  }, [onBackPress]);
}
